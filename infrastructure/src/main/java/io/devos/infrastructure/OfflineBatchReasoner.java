package io.devos.infrastructure;

import io.devos.application.*;
import io.devos.domain.*;
import java.time.Duration;
import java.util.*;

public final class OfflineBatchReasoner
    implements BatchInspectionPort, ReportSynthesisPort, ReportReviewPort {
  public BatchInsight inspect(
      Agent agent,
      String objective,
      AnalysisBatch batch,
      List<ToolResult> evidence,
      Duration timeout) {
    var observations = new ArrayList<AnalysisObservation>();
    for (var excerpt : batch.excerpts()) {
      if (observations.size() >= 4 || excerpt.content().startsWith("[DADOS SQL EXTENSOS")) continue;
      var lines = excerpt.content().split("\\n", -1);
      for (int i = 0; i < lines.length; i++)
        if (!lines[i].isBlank()) {
          String quote = lines[i].strip();
          quote = quote.substring(0, Math.min(quote.length(), 300));
          observations.add(
              new AnalysisObservation(
                  "B" + batch.index() + "-O" + (observations.size() + 1),
                  "Leitura estática de " + excerpt.path(),
                  "OBSERVATION",
                  "INFO",
                  "O trecho foi coletado pelo leitor local. O modo offline não realiza"
                      + " interpretação por IA.",
                  "Confirma a passagem do trecho pelo fluxo, sem comprovar correção do código.",
                  "Usar um provedor de IA para análise semântica e revisar as evidências"
                      + " apresentadas.",
                  List.of(
                      new EvidenceReference(
                          excerpt.path(),
                          excerpt.startLine() + i,
                          excerpt.startLine() + i,
                          quote))));
          break;
        }
    }
    return new BatchInsight(
        batch.index(),
        "Modo offline: " + batch.excerpts().size() + " trechos processados deterministicamente.",
        observations,
        List.of());
  }

  public AnalysisReport synthesize(
      Agent agent,
      String objective,
      RepositoryCoverage c,
      List<BatchInsight> insights,
      List<ToolResult> evidence,
      String memory,
      String feedback,
      Duration timeout) {
    return new AnalysisReport(
        "Foram coletados "
            + c.eligibleFiles()
            + " arquivos e processados "
            + c.completedBatches()
            + " de "
            + c.plannedBatches()
            + " lotes. Esta execução usa regras determinísticas, sem inferência de IA.",
        "O inventário inclui "
            + c.sourceFiles()
            + " arquivos de código e "
            + c.testFiles()
            + " arquivos de teste. O modo offline descreve cobertura e evidências textuais; não"
            + " determina se a arquitetura está correta.",
        "Foram identificados "
            + c.testFiles()
            + " arquivos de teste pelos caminhos e nomes. O conteúdo coletado foi percorrido por"
            + " lotes; nenhum teste do repositório foi executado. A quantidade de arquivos não mede"
            + " cobertura de testes.",
        List.of("Rastreabilidade dos trechos coletados, com arquivo e linha verificáveis."),
        List.of(
            "Conferir o inventário e os limites de leitura.",
            "Executar com Gemini ou Groq para obter interpretação semântica.",
            "Validar manualmente os riscos antes de alterar o repositório."),
        List.of(
            "Modo offline não avalia qualidade semântica.",
            "Análise estática: não compila nem executa o repositório.",
            "Dados SQL extensos podem ter representação estrutural explicitamente identificada."));
  }

  public ReviewDecision reviewReport(
      Agent agent,
      String objective,
      RepositoryCoverage c,
      List<BatchInsight> insights,
      List<ToolResult> evidence,
      AnalysisReport report,
      Duration timeout) {
    return new ReviewDecision(
        c.completedBatches() == c.plannedBatches() && !report.executiveSummary().isBlank(),
        "Revisão determinística da presença de cobertura, lotes e relatório. Não é validação"
            + " semântica por IA.");
  }
}
