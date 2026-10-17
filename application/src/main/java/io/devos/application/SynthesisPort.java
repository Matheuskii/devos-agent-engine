package io.devos.application;

import io.devos.domain.*;
import java.time.Duration;
import java.util.List;

public interface SynthesisPort {
  String summarize(
      Agent agent,
      String objective,
      RepositorySnapshot snapshot,
      List<ToolResult> evidence,
      String memory,
      String feedback,
      Duration timeout);
}
