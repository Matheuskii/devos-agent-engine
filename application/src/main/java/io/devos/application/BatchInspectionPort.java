package io.devos.application;

import io.devos.domain.Agent;
import java.time.Duration;
import java.util.*;

public interface BatchInspectionPort {
  BatchInsight inspect(
      Agent agent,
      String objective,
      AnalysisBatch batch,
      List<ToolResult> evidence,
      Duration timeout);
}
