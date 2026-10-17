package io.devos.domain;

import java.util.Set;

public record Finding(
    String rule, String severity, String path, int line, String evidence, String recommendation) {
  public Finding {
    rule = Checks.text(rule, "Regra");
    severity = Checks.text(severity, "Severidade");
    path = Checks.text(path, "Caminho");
    evidence = Checks.text(evidence, "Evidência");
    recommendation = Checks.text(recommendation, "Recomendação");
    if (!Set.of("INFO", "LOW", "MEDIUM", "HIGH").contains(severity) || line < 0)
      throw new IllegalArgumentException("Achado inválido");
  }
}
