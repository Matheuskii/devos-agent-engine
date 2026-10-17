package io.devos.application;

import io.devos.domain.*;

public record SourceFile(String path, String content) {
  public SourceFile {
    path = Checks.text(path, "Caminho");
    content = java.util.Objects.requireNonNull(content);
  }
}
