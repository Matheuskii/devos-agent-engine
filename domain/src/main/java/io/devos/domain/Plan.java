package io.devos.domain;

import java.util.*;

public record Plan(List<PlanStep> steps) {
  public Plan {
    steps = List.copyOf(steps);
    if (steps.isEmpty()) throw new IllegalArgumentException("Plano vazio");
    var ids = new HashSet<String>();
    for (var s : steps)
      if (!ids.add(s.id())) throw new IllegalArgumentException("Etapa duplicada: " + s.id());
    for (var s : steps)
      if (!ids.containsAll(s.dependencies()))
        throw new IllegalArgumentException("Dependência inexistente");
    var done = new HashSet<String>();
    while (done.size() < steps.size()) {
      int before = done.size();
      for (var s : steps) if (done.containsAll(s.dependencies())) done.add(s.id());
      if (done.size() == before) throw new IllegalArgumentException("Plano contém ciclo");
    }
  }

  public List<PlanStep> orderedSteps() {
    var remaining = new ArrayList<>(steps);
    var ordered = new ArrayList<PlanStep>();
    var done = new HashSet<String>();
    while (!remaining.isEmpty()) {
      var next =
          remaining.stream()
              .filter(s -> done.containsAll(s.dependencies()))
              .findFirst()
              .orElseThrow();
      ordered.add(next);
      done.add(next.id());
      remaining.remove(next);
    }
    return List.copyOf(ordered);
  }

  public void validateFor(Agent executor) {
    if (steps.size() > executor.policy().maxSteps())
      throw new IllegalArgumentException("Plano excede limite de etapas");
    for (var s : steps) executor.requireTool(s.tool());
  }
}
