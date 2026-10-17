package io.devos.application;

import io.devos.domain.*;
import java.util.*;

public record RunRecord(
    String id,
    String repositoryKey,
    String objective,
    String status,
    String mode,
    String startedAt,
    long elapsedMillis,
    int modelCalls,
    int revisions,
    String summary,
    List<Finding> findings,
    List<AgentRun> events,
    List<String> notices,
    String review,
    RepositoryCoverage coverage,
    AnalysisReport report,
    List<BatchInsight> batches) {
  public RunRecord {
    findings = List.copyOf(findings);
    events = List.copyOf(events);
    notices = List.copyOf(notices);
    batches = List.copyOf(batches);
  }

  public RunRecord(
      String id,
      String repositoryKey,
      String objective,
      String status,
      String mode,
      String startedAt,
      long elapsedMillis,
      int modelCalls,
      int revisions,
      String summary,
      List<Finding> findings,
      List<AgentRun> events,
      List<String> notices,
      String review) {
    this(
        id,
        repositoryKey,
        objective,
        status,
        mode,
        startedAt,
        elapsedMillis,
        modelCalls,
        revisions,
        summary,
        findings,
        events,
        notices,
        review,
        null,
        null,
        List.of());
  }
}
