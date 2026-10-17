package io.devos.domain;

import java.util.*;

public record AnalysisObservation(
    String id,
    String title,
    String kind,
    String priority,
    String explanation,
    String impact,
    String recommendation,
    List<EvidenceReference> evidence) {
  public AnalysisObservation {
    id = Checks.text(id, "ID");
    title = Checks.text(title, "Título");
    if (!Set.of("STRENGTH", "OBSERVATION", "RISK", "HYPOTHESIS").contains(kind))
      throw new IllegalArgumentException("Tipo de observação inválido");
    if (!Set.of("HIGH", "MEDIUM", "LOW", "INFO").contains(priority))
      throw new IllegalArgumentException("Prioridade inválida");
    explanation = Checks.text(explanation, "Explicação");
    impact = Checks.text(impact, "Impacto");
    recommendation = Checks.text(recommendation, "Recomendação");
    evidence = List.copyOf(evidence);
    if (evidence.isEmpty() || evidence.size() > 3)
      throw new IllegalArgumentException("Observação exige evidência");
  }
}
