package io.devos.domain;

import java.util.*;

public record Agent(
    UUID id,
    String name,
    Role role,
    String systemPrompt,
    String description,
    Set<String> capabilities,
    Set<ToolId> allowedToolIds,
    ExecutionPolicy policy,
    boolean enabled) {
  public Agent {
    Objects.requireNonNull(id);
    Objects.requireNonNull(role);
    Objects.requireNonNull(policy);
    name = Checks.text(name, "Nome");
    systemPrompt = Checks.text(systemPrompt, "Prompt");
    description = Checks.text(description, "Descrição");
    capabilities = Set.copyOf(capabilities);
    allowedToolIds = Set.copyOf(allowedToolIds);
  }

  public void requireTool(ToolId tool) {
    if (!enabled || !allowedToolIds.contains(tool))
      throw new IllegalStateException("Ferramenta não permitida: " + tool);
  }
}
