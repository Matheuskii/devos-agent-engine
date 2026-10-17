package io.devos.application;

import io.devos.domain.Agent;
import java.time.Duration;
import java.util.List;

@FunctionalInterface
public interface ObservationAuditPort {
  ReviewDecision audit(Agent reviewer, List<ObservationAuditContext> contexts, Duration timeout);
}
