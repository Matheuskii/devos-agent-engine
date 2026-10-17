package io.devos.domain;

import java.util.*;

public final class Task {
  private final UUID id;
  private final String objective;
  private TaskStatus status = TaskStatus.PENDING;

  public Task(UUID id, String objective) {
    this.id = Objects.requireNonNull(id);
    this.objective = Checks.text(objective, "Objetivo");
  }

  public UUID id() {
    return id;
  }

  public String objective() {
    return objective;
  }

  public TaskStatus status() {
    return status;
  }

  public void transitionTo(TaskStatus next) {
    boolean allowed =
        switch (status) {
          case PENDING -> next == TaskStatus.PLANNING;
          case PLANNING -> next == TaskStatus.EXECUTING || next == TaskStatus.FAILED;
          case EXECUTING -> next == TaskStatus.REVIEWING || next == TaskStatus.FAILED;
          case REVIEWING ->
              next == TaskStatus.EXECUTING
                  || next == TaskStatus.COMPLETED
                  || next == TaskStatus.REJECTED
                  || next == TaskStatus.FAILED;
          default -> false;
        };
    if (!allowed) throw new IllegalStateException("Transição inválida: " + status + " -> " + next);
    status = next;
  }
}
