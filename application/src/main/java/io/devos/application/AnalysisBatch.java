package io.devos.application;

import java.util.*;

public record AnalysisBatch(int index, int total, List<SourceExcerpt> excerpts) {
  public AnalysisBatch {
    excerpts = List.copyOf(excerpts);
    if (index < 1 || total < index || excerpts.isEmpty())
      throw new IllegalArgumentException("Lote inválido");
  }

  public int characters() {
    return excerpts.stream().mapToInt(e -> e.content().length()).sum();
  }
}
