package io.devos.application;

import java.util.*;

public record AnalysisReport(
    String executiveSummary,
    String architecture,
    String testing,
    List<String> strengths,
    List<String> actionPlan,
    List<String> limitations) {
  public AnalysisReport {
    executiveSummary = io.devos.domain.Checks.text(executiveSummary, "Resumo executivo");
    architecture = io.devos.domain.Checks.text(architecture, "Arquitetura");
    testing = io.devos.domain.Checks.text(testing, "Testes");
    strengths = List.copyOf(strengths);
    actionPlan = List.copyOf(actionPlan);
    limitations = List.copyOf(limitations);
  }
}
