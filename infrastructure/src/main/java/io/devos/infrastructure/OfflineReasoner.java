package io.devos.infrastructure;

import io.devos.application.*;
import io.devos.domain.*;
import java.time.Duration;
import java.util.*;

public final class OfflineReasoner implements PlanningPort, SynthesisPort, ReviewPort {
  public Plan plan(Agent agent, String objective, RepositorySnapshot snapshot, Duration timeout) {
    return new Plan(
        List.of(
            new PlanStep("inventory", ToolId.INVENTORY, Set.of()),
            new PlanStep("quality", ToolId.QUALITY, Set.of("inventory"))));
  }

  public String summarize(
      Agent agent,
      String objective,
      RepositorySnapshot snapshot,
      List<ToolResult> results,
      String memory,
      String feedback,
      Duration timeout) {
    return "Modo local determinístico, sem inferência de IA. Objetivo: "
        + objective
        + ". "
        + results.stream()
            .map(ToolResult::summary)
            .collect(java.util.stream.Collectors.joining(" "));
  }

  public ReviewDecision review(
      Agent agent,
      String objective,
      RepositorySnapshot snapshot,
      List<ToolResult> results,
      String summary,
      Duration timeout) {
    boolean approved =
        !summary.isBlank()
            && results.stream()
                .map(ToolResult::tool)
                .collect(java.util.stream.Collectors.toSet())
                .containsAll(Set.of(ToolId.INVENTORY, ToolId.QUALITY));
    return new ReviewDecision(
        approved,
        approved
            ? "Critérios estruturais atendidos: inventário, verificação e síntese presentes."
                + " Revisão local não avalia qualidade semântica."
            : "Evidências obrigatórias ausentes.");
  }
}
