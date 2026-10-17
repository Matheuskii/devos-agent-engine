package io.devos.application;

import io.devos.domain.*;
import java.util.*;

public record RepositorySnapshot(
    String repositoryKey, List<SourceFile> files, List<String> notices, boolean complete) {
  public RepositorySnapshot {
    files = List.copyOf(files);
    notices = List.copyOf(notices);
  }
}
