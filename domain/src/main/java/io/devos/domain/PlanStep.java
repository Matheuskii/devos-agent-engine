package io.devos.domain;

import java.util.*;

public record PlanStep(String id, ToolId tool, Set<String> dependencies) {
  public PlanStep {
    id = Checks.text(id, "ID da etapa");
    Objects.requireNonNull(tool);
    dependencies = Set.copyOf(dependencies);
    if (!id.matches("[a-zA-Z0-9_-]{1,40}"))
      throw new IllegalArgumentException("ID da etapa inválido");
    if (dependencies.contains(id)) throw new IllegalArgumentException("Etapa depende de si mesma");
  }
}
