package io.devos.application;

import io.devos.domain.*;

public record ReviewDecision(boolean approved, String feedback) {
  public ReviewDecision {
    feedback = Checks.text(feedback, "Parecer");
  }
}
