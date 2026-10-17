package io.devos.application;

import java.util.*;

public record RepositoryCoverage(
    int eligibleFiles,
    int sourceFiles,
    int testFiles,
    long originalCharacters,
    long submittedCharacters,
    int plannedBatches,
    int completedBatches,
    boolean collectionComplete,
    List<String> reducedDataFiles,
    List<String> includedPaths) {
  public RepositoryCoverage {
    reducedDataFiles = List.copyOf(reducedDataFiles);
    includedPaths = List.copyOf(includedPaths);
  }

  public boolean processingComplete() {
    return collectionComplete && completedBatches == plannedBatches;
  }

  public static RepositoryCoverage of(RepositorySnapshot snapshot, BatchPlan plan, int completed) {
    int tests = (int) snapshot.files().stream().filter(f -> isTest(f.path())).count();
    int source =
        (int)
            snapshot.files().stream()
                .filter(
                    f ->
                        !isTest(f.path())
                            && f.path().matches(".*\\.(java|kt|js|jsx|ts|tsx|py|dart|cs|go|rs)$"))
                .count();
    return new RepositoryCoverage(
        snapshot.files().size(),
        source,
        tests,
        plan.originalCharacters(),
        plan.submittedCharacters(),
        plan.batches().size(),
        completed,
        snapshot.complete(),
        plan.reductions(),
        snapshot.files().stream().map(SourceFile::path).toList());
  }

  public static boolean isTest(String path) {
    return path.matches("(?i).*(?:^|/)(?:test|tests|__tests__)/.*")
        || path.matches("(?i).*(?:test|spec)\\.[^.]+$");
  }
}
