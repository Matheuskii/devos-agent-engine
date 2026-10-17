package io.devos.application;

import io.devos.domain.Agent;
import java.time.Duration;
import java.util.*;

public interface ReportSynthesisPort {
  AnalysisReport synthesize(
      Agent agent,
      String objective,
      RepositoryCoverage coverage,
      List<BatchInsight> insights,
      List<ToolResult> evidence,
      String memory,
      String feedback,
      Duration timeout);
}
