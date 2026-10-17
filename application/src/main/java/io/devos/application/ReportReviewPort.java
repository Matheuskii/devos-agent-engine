package io.devos.application;

import io.devos.domain.Agent;
import java.time.Duration;
import java.util.*;

public interface ReportReviewPort {
  ReviewDecision reviewReport(
      Agent agent,
      String objective,
      RepositoryCoverage coverage,
      List<BatchInsight> insights,
      List<ToolResult> evidence,
      AnalysisReport report,
      Duration timeout);
}
