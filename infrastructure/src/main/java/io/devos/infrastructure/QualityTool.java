package io.devos.infrastructure;

import io.devos.application.*;
import io.devos.domain.*;
import java.util.*;

public final class QualityTool implements ToolPort {
  public ToolId id() {
    return ToolId.QUALITY;
  }

  public ToolResult execute(RepositorySnapshot snapshot) {
    var findings = new ArrayList<Finding>();
    boolean readme =
        snapshot.files().stream().anyMatch(f -> f.path().equalsIgnoreCase("README.md"));
    if (!readme)
      findings.add(
          new Finding(
              "README_NOT_OBSERVED",
              "LOW",
              ".",
              0,
              "README.md não encontrado na amostra elegível.",
              "Verificar a documentação de instalação, uso e testes."));
    boolean tests =
        snapshot.files().stream()
            .anyMatch(
                f ->
                    f.path().matches("(?i).*(?:/test/|/tests/|test[^/]*\\.|spec\\.).*")
                        || f.path().startsWith("tests/"));
    if (!tests)
      findings.add(
          new Finding(
              "TESTS_NOT_OBSERVED",
              "MEDIUM",
              ".",
              0,
              "Arquivos de teste não encontrados na amostra elegível.",
              "Confirmar a existência de testes e priorizar regras críticas."));
    var markers = java.util.regex.Pattern.compile("\\b(TODO|FIXME)\\b");
    for (var file : snapshot.files()) {
      String[] lines = file.content().split("\\R", -1);
      for (int i = 0; i < lines.length && findings.size() < 50; i++) {
        if (markers.matcher(lines[i]).find())
          findings.add(
              new Finding(
                  "PENDING_MARKER",
                  "INFO",
                  file.path(),
                  i + 1,
                  lines[i].strip().substring(0, Math.min(lines[i].strip().length(), 180)),
                  "Revisar o marcador no contexto; ele não comprova um defeito."));
      }
    }
    return new ToolResult(
        id(),
        "Verificação heurística: "
            + findings.size()
            + " observações; não equivale a compilação, teste ou auditoria completa.",
        findings);
  }
}
