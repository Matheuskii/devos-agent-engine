package io.devos.cli;

import io.devos.application.*;
import io.devos.domain.*;
import io.devos.infrastructure.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;

public final class Main {
  private Main() {}

  public static void main(String[] args) {
    System.exit(run(args, System.getenv(), System.out, System.err));
  }

  public static int run(
      String[] args, Map<String, String> env, java.io.PrintStream out, java.io.PrintStream err) {
    if (args.length == 0 || Arrays.asList(args).contains("--help")) {
      out.println(usage());
      return 0;
    }
    try {
      if (args.length < 2 || !args[0].equals("analyze"))
        throw new IllegalArgumentException("Use analyze CAMINHO");
      var options = new HashMap<String, String>();
      for (int i = 2; i < args.length; i += 2) {
        if (!Set.of("--objective", "--output", "--provider", "--memory").contains(args[i])
            || i + 1 >= args.length
            || options.put(args[i], args[i + 1]) != null)
          throw new IllegalArgumentException("Opção inválida, repetida ou sem valor: " + args[i]);
      }
      String provider =
          options.getOrDefault("--provider", env.getOrDefault("DEVOS_PROVIDER", "offline"));
      String memoryMode =
          options.getOrDefault("--memory", env.getOrDefault("DEVOS_MEMORY", "none"));
      String objective =
          options.getOrDefault(
              "--objective",
              "Analisar estrutura, documentação e sinais de qualidade do repositório");
      Path output = Path.of(options.getOrDefault("--output", ".devos/runs"));
      PlanningPort planning;
      SynthesisPort synthesis;
      ReviewPort review;
      var http = new JsonHttp();
      switch (provider) {
        case "offline" -> {
          var local = new OfflineReasoner();
          planning = local;
          synthesis = local;
          review = local;
        }
        case "gemini", "groq" -> {
          String key = required(env, provider.equals("gemini") ? "GEMINI_API_KEY" : "GROQ_API_KEY");
          String model = required(env, "DEVOS_MODEL");
          var kind =
              provider.equals("gemini")
                  ? ProviderModelClient.Provider.GEMINI
                  : ProviderModelClient.Provider.GROQ;
          URI endpoint =
              URI.create(
                  provider.equals("gemini")
                      ? "https://generativelanguage.googleapis.com/v1beta"
                      : "https://api.groq.com/openai/v1/chat/completions");
          var remote = new JsonReasoner(new ProviderModelClient(kind, key, model, endpoint, http));
          planning = remote;
          synthesis = remote;
          review = remote;
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
      var policy = ExecutionPolicy.defaults();
      var agents =
          List.of(
              agent(
                  "planner",
                  Role.PLANNER,
                  "Planeje uma análise de repositório com as ferramentas disponíveis.",
                  Set.of(),
                  policy),
              agent(
                  "executor",
                  Role.EXECUTOR,
                  "Sintetize somente evidências observadas; diferencie hipóteses de fatos.",
                  Set.of(ToolId.INVENTORY, ToolId.QUALITY),
                  policy),
              agent(
                  "reviewer",
                  Role.REVIEWER,
                  "Avalie criticamente a síntese comparando com as evidências fornecidas.",
                  Set.of(),
                  policy));
      var engine =
          new AnalysisEngine(
              new LocalRepository(),
              planning,
              synthesis,
              review,
              List.of(new InventoryTool(), new QualityTool()),
              new FileRunRepository(output),
              memory,
              agents,
              provider);
      var result = engine.analyze(Path.of(args[1]), objective);
      out.println(
          "Estado: "
              + result.status()
              + " | Modo: "
              + result.mode()
              + " | Chamadas: "
              + result.modelCalls());
      out.println("Relatório: " + output.toAbsolutePath().normalize().resolve(result.id() + ".md"));
      out.println(
          "Registro JSON: " + output.toAbsolutePath().normalize().resolve(result.id() + ".json"));
      if (!result.status().equals("COMPLETED")) err.println("Parecer: " + result.review());
      return result.status().equals("COMPLETED") ? 0 : 1;
    } catch (RuntimeException ex) {
      err.println("Erro: " + ex.getMessage());
      return 2;
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
           DevOS Agent Engine 0.1.0 — Java 21
           Uso: java -jar cli/target/cli-0.1.0.jar analyze CAMINHO [opções]
             --provider offline|gemini|groq   padrão: offline
             --memory none|qdrant           padrão: none
             --objective TEXTO             objetivo da análise
             --output DIRETORIO            padrão: .devos/runs
           Online: defina DEVOS_MODEL e GEMINI_API_KEY ou GROQ_API_KEY.
           Qdrant: servidor local e GEMINI_API_KEY para embeddings semânticos.
           """;
  }
}
