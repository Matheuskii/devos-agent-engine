package io.devos.domain;

import java.time.Duration;

public record ExecutionPolicy(int maxSteps, int maxModelCalls, int maxRevisions, Duration timeout) {
  public ExecutionPolicy {
    if (maxSteps < 1 || maxSteps > 20 || maxModelCalls < 3 || maxRevisions < 0 || maxRevisions > 5)
      throw new IllegalArgumentException("Limites de execução inválidos");
    if (timeout == null
        || timeout.isZero()
        || timeout.isNegative()
        || timeout.compareTo(Duration.ofHours(1)) > 0)
      throw new IllegalArgumentException("Timeout deve estar entre 1ns e 1h");
  }

  public static ExecutionPolicy defaults() {
    return new ExecutionPolicy(6, 7, 2, Duration.ofMinutes(3));
  }
}
