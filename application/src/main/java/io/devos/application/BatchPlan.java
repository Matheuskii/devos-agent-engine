package io.devos.application;

import java.util.*;

public record BatchPlan(
    List<AnalysisBatch> batches,
    List<SourceFile> reducedFiles,
    List<String> reductions,
    long originalCharacters,
    long submittedCharacters) {
  public BatchPlan {
    batches = List.copyOf(batches);
    reducedFiles = List.copyOf(reducedFiles);
    reductions = List.copyOf(reductions);
  }
}
