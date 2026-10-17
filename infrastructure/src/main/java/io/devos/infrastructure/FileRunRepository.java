package io.devos.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.devos.application.*;
import io.devos.domain.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class FileRunRepository implements RunRepository {
  private final Path directory;
  private final ObjectMapper mapper = new ObjectMapper();

  public FileRunRepository(Path directory) {
    this.directory = directory.toAbsolutePath().normalize();
  }

  public void save(RunRecord record) {
    try {
      Files.createDirectories(directory);
      atomic(
          record.id() + ".json",
          mapper.writerWithDefaultPrettyPrinter().writeValueAsString(record));
      String markdown = markdown(record);
      atomic(record.id() + ".md", markdown);
      atomic("latest.json", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(record));
      atomic("latest.md", markdown);
    } catch (IOException ex) {
      throw new IllegalStateException("Não foi possível persistir o relatório de execução");
    }
  }

  private void atomic(String filename, String text) throws IOException {
    Path temp = Files.createTempFile(directory, ".writing-", ".tmp");
    try {
      Files.writeString(temp, text, StandardCharsets.UTF_8);
      try {
        Files.move(
            temp,
            directory.resolve(filename),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(temp, directory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  public static String markdown(RunRecord r) {
    if (r.coverage() != null) return DetailedReportRenderer.render(r);
    var out = new StringBuilder("# DevOS Agent Engine — análise de repositório\n\n");
    out.append("Execução: ")
        .append(r.id())
        .append("\n\nEstado: **")
        .append(r.status())
        .append("** | Modo: ")
        .append(r.mode())
        .append("\n\n");
    out.append("Objetivo: ")
        .append(escape(r.objective()))
        .append("\n\n## Síntese\n\n")
        .append(escape(r.summary()))
        .append("\n\n## Evidências heurísticas\n\n");
    for (var f : r.findings())
      out.append("- **")
          .append(f.severity())
          .append(" / ")
          .append(f.rule())
          .append("** — ")
          .append(escape(f.path()))
          .append(":")
          .append(f.line())
          .append("\n  ")
          .append(escape(f.evidence()))
          .append("\n  Recomendação: ")
          .append(escape(f.recommendation()))
          .append("\n\n");
    if (r.findings().isEmpty())
      out.append(
          "Nenhum achado pelas regras aplicadas. Isso não comprova ausência de problemas.\n\n");
    out.append("## Parecer\n\n").append(escape(r.review())).append("\n\n## Rastreabilidade\n\n");
    for (var e : r.events())
      out.append("- ")
          .append(e.role())
          .append(" / ")
          .append(escape(e.operation()))
          .append(" / ")
          .append(e.status())
          .append(": ")
          .append(escape(e.detail()))
          .append("\n");
    out.append("\nChamadas de raciocínio: ")
        .append(r.modelCalls())
        .append("; revisões adicionais: ")
        .append(r.revisions())
        .append("; duração: ")
        .append(r.elapsedMillis())
        .append(" ms.\n\n");
    for (var notice : r.notices()) out.append("- ").append(escape(notice)).append("\n");
    out.append(
        "\n"
            + "Limites: análise estática por amostragem; não compila nem executa o código"
            + " analisado. Parecer de IA é uma avaliação probabilística.\n");
    return out.toString();
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
