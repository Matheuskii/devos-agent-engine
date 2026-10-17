package io.devos.application;

import io.devos.domain.Checks;
import java.util.Set;

public record ReviewDecision(
    boolean approved, String feedback, Set<String> rejectedObservationIds) {
  public ReviewDecision {
    feedback = Checks.text(feedback, "Parecer");
    rejectedObservationIds = Set.copyOf(rejectedObservationIds);
    if (!rejectedObservationIds.isEmpty()) approved = false;
  }

  public ReviewDecision(boolean approved, String feedback) {
    this(approved, feedback, Set.of());
  }
}
