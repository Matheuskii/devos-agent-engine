package io.devos.application;

import io.devos.domain.*;
import java.time.Duration;
import java.util.List;

public interface ReviewPort {
  ReviewDecision review(
      Agent agent,
      String objective,
      RepositorySnapshot snapshot,
      List<ToolResult> evidence,
      String summary,
      Duration timeout);
}
