package io.devos.application;

import io.devos.domain.*;
import java.util.List;

public record ToolResult(ToolId tool, String summary, List<Finding> findings) {
  public ToolResult {
    java.util.Objects.requireNonNull(tool);
    summary = Checks.text(summary, "Resumo");
    findings = List.copyOf(findings);
  }
}
