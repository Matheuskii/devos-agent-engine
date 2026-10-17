package io.devos.application;

import io.devos.domain.*;
import java.time.Duration;

public interface PlanningPort {
  Plan plan(Agent agent, String objective, RepositorySnapshot snapshot, Duration timeout);
}
