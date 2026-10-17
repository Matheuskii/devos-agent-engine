package io.devos.application;

public record SourceExcerpt(
    String path, int startLine, int endLine, String content, boolean completeFile) {
  public SourceExcerpt(String path, int startLine, int endLine, String content) {
    this(path, startLine, endLine, content, false);
  }

  public SourceExcerpt {
    java.util.Objects.requireNonNull(path);
    java.util.Objects.requireNonNull(content);
    if (startLine < 1 || endLine < startLine)
      throw new IllegalArgumentException("Intervalo de trecho inválido");
  }

  public String numberedContent() {
    String[] lines = content.split("\\n", -1);
    var out = new StringBuilder();
    for (int i = 0; i < lines.length; i++)
      out.append(startLine + i).append(" | ").append(lines[i]).append('\n');
    return out.toString();
  }
}
