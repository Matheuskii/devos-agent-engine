package io.devos.application;

import io.devos.domain.*;
import java.time.Duration;
import java.util.function.LongSupplier;

public final class ExecutionBudget {
  private final ExecutionPolicy policy;
  private final long started;
  private final LongSupplier nanoTime;
  private int calls;

  public ExecutionBudget(ExecutionPolicy policy) {
    this(policy, System::nanoTime);
  }

  public ExecutionBudget(ExecutionPolicy policy, LongSupplier nanoTime) {
    this.policy = policy;
    this.nanoTime = nanoTime;
    this.started = nanoTime.getAsLong();
  }

  public Duration remaining() {
    long left = policy.timeout().toNanos() - (nanoTime.getAsLong() - started);
    if (left <= 0) throw new IllegalStateException("Tempo de execução excedido");
    return Duration.ofNanos(left);
  }

  public Duration reserveModelCall() {
    var remaining = remaining();
    if (calls >= policy.maxModelCalls())
      throw new IllegalStateException("Limite de chamadas ao modelo excedido");
    calls++;
    return remaining;
  }

  public int calls() {
    return calls;
  }
}
