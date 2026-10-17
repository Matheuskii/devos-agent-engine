package io.devos.infrastructure;

import io.devos.application.*;
import io.devos.domain.*;
import java.util.*;

public final class DetailedReportRenderer {
  private DetailedReportRenderer() {}

  public static String render(RunRecord r) {
    var out = new StringBuilder("# Análise técnica do repositório\n\n");
    out.append("**Objetivo:** ").append(escape(r.objective())).append("\n\n");
    out.append("**Estado:** ")
        .append(r.status())
        .append(" · **Modo:** ")
        .append(r.mode())
        .append(" · **Execução:** ")
        .append(r.id())
        .append("\n\n");
    out.append(
        "Este relatório descreve uma análise estática assistida. A aprovação se refere à"
            + " sustentação do relatório pelas evidências, e não à ausência de defeitos no"
            + " código.\n\n");
    var c = r.coverage();
    out.append("## 1. Escopo e cobertura\n\n| Medida | Resultado |\n|---|---:|\n");
    row(out, "Arquivos elegíveis coletados", c.eligibleFiles());
    row(out, "Arquivos de código fora da árvore de testes", c.sourceFiles());
    row(out, "Arquivos de teste e apoio", c.testFiles());
    long mainJava =
        c.includedPaths().stream()
            .filter(p -> p.startsWith("src/main/") && p.endsWith(".java"))
            .count();
    long testJava =
        c.includedPaths().stream()
            .filter(p -> p.startsWith("src/test/") && p.endsWith(".java"))
            .count();
    if (mainJava + testJava > 0) {
      row(out, "Arquivos Java em src/main", mainJava);
      row(out, "Arquivos Java em src/test", testJava);
    }
    row(out, "Caracteres lidos", c.originalCharacters());
    row(out, "Caracteres preparados para os lotes", c.submittedCharacters());
    row(out, "Lotes concluídos / planejados", c.completedBatches() + " / " + c.plannedBatches());
    row(
        out,
        "Coleta dentro dos limites",
        c.collectionComplete()
            ? "Completa nos tipos e exclusões configurados"
            : "Parcial — consultar limitações");
    out.append(
        "\n"
            + "**Como interpretar:** arquivos de teste e apoio foram identificados por caminhos e"
            + " nomes e lidos estaticamente. A contagem não representa cobertura de testes; nenhum"
            + " teste do repositório foi executado. Arquivos de ambiente, dependências, binários e"
            + " outros itens excluídos não entram nesta coleta.\n\n");
    if (!c.reducedDataFiles().isEmpty()) {
      out.append(
          "### Dados extensos tratados separadamente\n\n"
              + "Os arquivos abaixo foram lidos pelo programa, mas representados estruturalmente"
              + " para a IA. Seus dados individuais não receberam revisão semântica integral:\n\n");
      for (var s : c.reducedDataFiles()) out.append("- ").append(escape(s)).append("\n");
      out.append('\n');
    }
    if (r.report() == null)
      out.append(
          "## 2. Resultado parcial\n\n"
              + "O relatório final ainda não foi produzido. Os lotes e observações já concluídos"
              + " estão preservados no registro JSON. Não interprete este arquivo como análise"
              + " completa.\n\n");
    else {
      var report = r.report();
      section(out, "2. Resumo executivo", report.executiveSummary());
      section(out, "3. Arquitetura observada", report.architecture());
      section(out, "4. Testes e confiabilidade", report.testing());
      out.append("## 5. Pontos fortes\n\n");
      for (var s : report.strengths()) out.append("- ").append(escape(s)).append("\n");
      out.append('\n');
    }
    var observations = observations(r);
    var risks =
        observations.stream()
            .filter(o -> o.kind().equals("RISK") || o.kind().equals("HYPOTHESIS"))
            .toList();
    out.append("## 6. Prioridades com evidências\n\n");
    if (risks.isEmpty())
      out.append(
          "Não foram registrados riscos com referências textuais verificadas nesta execução. Isso"
              + " não comprova ausência de defeitos.\n\n");
    else {
      out.append(
          "Prioridade indica impacto potencial e ordem sugerida de investigação. **Risco** e"
              + " **hipótese** não são defeitos confirmados por execução.\n\n"
              + "| ID | Prioridade | Tipo | Tema | Evidência |\n"
              + "|---|---|---|---|---|\n");
      for (var o : risks.stream().limit(15).toList()) {
        var e = o.evidence().getFirst();
        out.append("| ")
            .append(o.id())
            .append(" | ")
            .append(priority(o.priority()))
            .append(" | ")
            .append(kind(o.kind()))
            .append(" | ")
            .append(cell(o.title()))
            .append(" | ")
            .append(cell(e.path()))
            .append(':')
            .append(e.startLine())
            .append(" |\n");
      }
      out.append('\n');
    }
    out.append(
        "## 7. Observações explicadas\n\n"
            + "Cada trecho abaixo foi conferido contra o arquivo e o intervalo de linhas do lote."
            + " Essa conferência valida a referência textual; a interpretação ainda exige revisão"
            + " técnica. O JSON preserva todas as observações aceitas.\n\n");
    for (var o :
        observations.stream().filter(x -> !x.kind().equals("STRENGTH")).limit(24).toList()) {
      out.append("### ").append(o.id()).append(" — ").append(escape(o.title())).append("\n\n");
      out.append("**Tipo:** ")
          .append(kind(o.kind()))
          .append(" · **Prioridade:** ")
          .append(priority(o.priority()))
          .append("\n\n");
      out.append("**O que foi observado:** ")
          .append(escape(o.explanation()))
          .append("\n\n**Por que importa:** ")
          .append(escape(o.impact()))
          .append("\n\n**Próximo passo:** ")
          .append(escape(o.recommendation()))
          .append("\n\n");
      for (var e : o.evidence()) {
        out.append("**Evidência:** ")
            .append(escape(e.path()))
            .append(':')
            .append(e.startLine())
            .append('-')
            .append(e.endLine())
            .append("\n\n");
        String fence = "```";
        while (e.quote().contains(fence)) fence += "`";
        out.append(fence)
            .append("text\n")
            .append(e.quote())
            .append('\n')
            .append(fence)
            .append("\n\n");
      }
    }
    if (r.report() != null) {
      out.append("## 8. Plano de ação sugerido\n\n");
      int n = 0;
      for (var a : r.report().actionPlan())
        out.append(++n).append(". ").append(escape(a)).append("\n\n");
      out.append("## 9. Limitações e cuidados de interpretação\n\n");
      for (var s : r.report().limitations()) out.append("- ").append(escape(s)).append("\n");
      out.append('\n');
    }
    if (!r.findings().isEmpty()) {
      out.append("### Verificações determinísticas\n\n");
      for (var f : r.findings())
        out.append("- **")
            .append(f.rule())
            .append("** — ")
            .append(escape(f.path()))
            .append(':')
            .append(f.line())
            .append(": ")
            .append(escape(f.evidence()))
            .append(" Recomendação: ")
            .append(escape(f.recommendation()))
            .append("\n");
      out.append('\n');
    }
    if (!r.notices().isEmpty()) {
      out.append("### Avisos da execução\n\n");
      for (var n : r.notices()) out.append("- ").append(escape(n)).append("\n");
      out.append('\n');
    }
    section(
        out,
        "10. Parecer do revisor",
        r.review().isBlank() ? "Revisão ainda não concluída." : r.review());
    out.append(
        "## Apêndice: rastreabilidade\n\n"
            + "| Lote | Observações verificadas | Situação |\n"
            + "|---|---:|---|\n");
    for (var b : r.batches())
      out.append("| ")
          .append(b.index())
          .append(" | ")
          .append(b.observations().size())
          .append(" | Processado |\n");
    out.append("\nChamadas de raciocínio: ")
        .append(r.modelCalls())
        .append("; revisões adicionais: ")
        .append(r.revisions())
        .append("; duração: ")
        .append(String.format(Locale.ROOT, "%.1f", r.elapsedMillis() / 1000.0))
        .append(" s.\n\n");
    out.append(
            "Inventário completo de caminhos elegíveis, todos os lotes, observações e eventos:"
                + " [registro JSON](")
        .append(r.id())
        .append(".json).\n");
    return out.toString();
  }

  private static List<AnalysisObservation> observations(RunRecord r) {
    var unique = new LinkedHashMap<String, AnalysisObservation>();
    for (var b : r.batches())
      for (var o : b.observations()) {
        var e = o.evidence().getFirst();
        unique.putIfAbsent(o.kind() + o.title() + e.path() + e.startLine(), o);
      }
    return unique.values().stream()
        .sorted(
            Comparator.comparingInt((AnalysisObservation o) -> rank(o.priority()))
                .thenComparing(AnalysisObservation::id))
        .toList();
  }

  private static int rank(String p) {
    return switch (p) {
      case "HIGH" -> 0;
      case "MEDIUM" -> 1;
      case "LOW" -> 2;
      default -> 3;
    };
  }

  private static String priority(String p) {
    return switch (p) {
      case "HIGH" -> "Alta";
      case "MEDIUM" -> "Média";
      case "LOW" -> "Baixa";
      default -> "Informativa";
    };
  }

  private static String kind(String k) {
    return switch (k) {
      case "RISK" -> "Risco";
      case "HYPOTHESIS" -> "Hipótese";
      case "STRENGTH" -> "Ponto forte";
      default -> "Observação";
    };
  }

  private static void row(StringBuilder out, String label, Object value) {
    out.append("| ").append(label).append(" | ").append(value).append(" |\n");
  }

  private static void section(StringBuilder out, String title, String text) {
    out.append("## ").append(title).append("\n\n").append(escape(text)).append("\n\n");
  }

  private static String cell(String s) {
    return escape(s).replace("|", "\\|").replace("\n", " ");
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
