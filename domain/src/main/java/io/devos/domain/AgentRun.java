package io.devos.domain;

import java.util.*;

public record AgentRun(UUID agentId, Role role, String operation, String status, String detail) {
  public AgentRun {
    Objects.requireNonNull(agentId);
    Objects.requireNonNull(role);
    operation = Checks.text(operation, "Operação");
    if (!Set.of("SUCCEEDED", "FAILED").contains(status))
      throw new IllegalArgumentException("Status inválido");
    detail = Objects.requireNonNull(detail);
  }
}
