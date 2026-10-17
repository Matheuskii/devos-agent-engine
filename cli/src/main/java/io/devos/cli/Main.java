package io.devos.cli;

import io.devos.application.*;
import io.devos.domain.*;
import io.devos.infrastructure.*;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

public final class Main {
  private Main() {}

  public static void main(String[] args) {
    System.exit(run(args, System.getenv(), System.out, System.err));
  }

  public static int run(String[] args, Map<String, String> env, PrintStream out, PrintStream err) {
    if (args.length == 0 || Arrays.asList(args).contains("--help")) {
      out.println(usage());
      return 0;
    }
    try {
      if (args.length < 2 || !args[0].equals("analyze"))
        throw new IllegalArgumentException("Use analyze CAMINHO");
      var options = new HashMap<String, String>();
      var allowed =
          Set.of(
              "--objective",
              "--output",
              "--provider",
              "--memory",
              "--scope",
              "--batch-chars",
              "--max-batches",
              "--max-calls",
              "--timeout-seconds",
              "--delay-ms");
      for (int i = 2; i < args.length; i += 2)
        if (!allowed.contains(args[i])
            || i + 1 >= args.length
            || options.put(args[i], args[i + 1]) != null)
          throw new IllegalArgumentException("Opção inválida, repetida ou sem valor: " + args[i]);
      String scope = options.getOrDefault("--scope", "full");
      if (!Set.of("full", "sample").contains(scope))
        throw new IllegalArgumentException("Scope deve ser full ou sample");
      boolean full = scope.equals("full");
      String provider =
          options.getOrDefault("--provider", env.getOrDefault("DEVOS_PROVIDER", "offline"));
      String memoryMode =
          options.getOrDefault("--memory", env.getOrDefault("DEVOS_MEMORY", "none"));
      String objective =
          options.getOrDefault(
              "--objective",
              "Analisar arquitetura, testes, qualidade e prioridades práticas do repositório,"
                  + " distinguindo fatos, riscos e hipóteses.");
      Path output = Path.of(options.getOrDefault("--output", "devos-output"));
      var policy =
          new ExecutionPolicy(
              6,
              number(options, "--max-calls", full ? 80 : 7, 3, 1000),
              2,
              Duration.ofSeconds(number(options, "--timeout-seconds", full ? 900 : 180, 1, 3600)));
      var settings =
          new BatchSettings(
              number(options, "--batch-chars", 64000, 2000, 200000),
              number(options, "--max-batches", 64, 1, 500),
              Duration.ofMillis(number(options, "--delay-ms", 1000, 0, 30000)));
      PlanningPort planning;
      SynthesisPort synthesis;
      ReviewPort review;
      BatchInspectionPort inspection;
      ReportSynthesisPort reportWriter;
      ReportReviewPort reportReviewer;
      ObservationAuditPort auditor;
      var http = new JsonHttp();
      switch (provider) {
        case "offline" -> {
          var local = new OfflineReasoner();
          planning = local;
          synthesis = local;
          review = local;
          var detailed = new OfflineBatchReasoner();
          inspection = detailed;
          reportWriter = detailed;
          reportReviewer = detailed;
          auditor =
              (a, c, t) -> new ReviewDecision(true, "Modo offline: sem auditoria semântica de IA.");
        }
        case "gemini", "groq" -> {
          String key = required(env, provider.equals("gemini") ? "GEMINI_API_KEY" : "GROQ_API_KEY"),
              model = required(env, "DEVOS_MODEL");
          var kind =
              provider.equals("gemini")
                  ? ProviderModelClient.Provider.GEMINI
                  : ProviderModelClient.Provider.GROQ;
          URI endpoint =
              URI.create(
                  provider.equals("gemini")
                      ? "https://generativelanguage.googleapis.com/v1beta"
                      : "https://api.groq.com/openai/v1/chat/completions");
          var client =
              new ProviderModelClient(kind, key, model, endpoint, http, full ? 6000 : 2500);
          var remote = new JsonReasoner(client);
          planning = remote;
          synthesis = remote;
          review = remote;
          var detailed = new JsonBatchReasoner(client);
          inspection = detailed;
          reportWriter = detailed;
          reportReviewer = detailed;
          auditor = new JsonObservationAuditor(client);
        }
        default -> throw new IllegalArgumentException("Provider deve ser offline, gemini ou groq");
      }
      MemoryPort memory =
          switch (memoryMode) {
            case "none" -> new NoMemory();
            case "qdrant" ->
                new QdrantMemory(
                    http,
                    new GeminiEmbedding(
                        http,
                        required(env, "GEMINI_API_KEY"),
                        env.getOrDefault("DEVOS_EMBEDDING_MODEL", "gemini-embedding-001"),
                        768,
                        URI.create("https://generativelanguage.googleapis.com/v1beta")),
                    URI.create(env.getOrDefault("QDRANT_URL", "http://127.0.0.1:6333")));
            default -> throw new IllegalArgumentException("Memory deve ser none ou qdrant");
          };
      var agents =
          List.of(
              agent(
                  "planner",
                  Role.PLANNER,
                  "Planeje a análise com as ferramentas disponíveis, sem repetir ferramentas.",
                  Set.of(),
                  policy),
              agent(
                  "executor",
                  Role.EXECUTOR,
                  "Explique o código com evidências por arquivo e linha; diferencie observação,"
                      + " ponto forte, risco e hipótese.",
                  Set.of(ToolId.INVENTORY, ToolId.QUALITY),
                  policy),
              agent(
                  "reviewer",
                  Role.REVIEWER,
                  "Avalie precisão e clareza do relatório, sem confundir defeitos do repositório"
                      + " com defeitos do relatório.",
                  Set.of(),
                  policy));
      RunRecord result;
      var tools = List.<ToolPort>of(new InventoryTool(), new QualityTool());
      var storage = new FileRunRepository(output);
      if (full) {
        result =
            new FullAnalysisEngine(
                    new LocalRepository(5000, 24000000, 8000000),
                    planning,
                    inspection,
                    reportWriter,
                    reportReviewer,
                    auditor,
                    tools,
                    storage,
                    memory,
                    agents,
                    settings,
                    out::println,
                    provider)
                .analyze(Path.of(args[1]), objective);
      } else
        result =
            new AnalysisEngine(
                    new LocalRepository(),
                    planning,
                    synthesis,
                    review,
                    tools,
                    storage,
                    memory,
                    agents,
                    provider)
                .analyze(Path.of(args[1]), objective);
      out.println(
          "Estado: "
              + result.status()
              + " | Modo: "
              + result.mode()
              + " | Chamadas: "
              + result.modelCalls());
      out.println("Relatório: " + output.toAbsolutePath().normalize().resolve("latest.md"));
      out.println("Registro JSON: " + output.toAbsolutePath().normalize().resolve("latest.json"));
      out.println(
          "Histórico desta execução: "
              + output.toAbsolutePath().normalize().resolve(result.id() + ".md"));
      if (!result.status().equals("COMPLETED")) err.println("Parecer: " + result.review());
      return result.status().equals("COMPLETED") ? 0 : 1;
    } catch (RuntimeException ex) {
      err.println("Erro: " + ex.getMessage());
      return 2;
    }
  }

  private static int number(
      Map<String, String> options, String key, int fallback, int min, int max) {
    try {
      int value = Integer.parseInt(options.getOrDefault(key, Integer.toString(fallback)));
      if (value < min || value > max) throw new NumberFormatException();
      return value;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + " deve estar entre " + min + " e " + max);
    }
  }

  private static String required(Map<String, String> env, String name) {
    return Checks.text(env.get(name), name);
  }

  private static Agent agent(
      String name, Role role, String prompt, Set<ToolId> tools, ExecutionPolicy policy) {
    return new Agent(
        UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        name,
        role,
        prompt,
        "Agente " + role,
        Set.of(role.name()),
        tools,
        policy,
        true);
  }

  private static String usage() {
    return """
DevOS Agent Engine — Java 21
Uso: java -jar cli/target/cli-0.1.0.jar analyze CAMINHO [opções]
  --provider offline|gemini|groq    padrão: offline
  --memory none|qdrant            padrão: none
  --scope full|sample             padrão: full (todos os arquivos elegíveis, por lotes)
  --objective TEXTO               objetivo da análise
  --output DIRETORIO              padrão: devos-output; abra latest.md
  --batch-chars N                 padrão: 64000 caracteres por lote
  --max-batches N                 padrão: 64
  --max-calls N                   padrão: 80 em full; 7 em sample
  --timeout-seconds N             padrão: 900 em full; 180 em sample
  --delay-ms N                    padrão: 1000 entre lotes
Online: defina DEVOS_MODEL e GEMINI_API_KEY ou GROQ_API_KEY.
O arquivo .env não é carregado automaticamente pela CLI.
O modo full lê código e testes; dados SQL extensos têm representação estrutural informada.
""";
  }
}
