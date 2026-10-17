package io.devos.application;

import io.devos.domain.*;
import java.util.*;

public final class EvidenceValidator {
  public BatchInsight validate(
      BatchInsight insight, AnalysisBatch batch, Set<String> reducedPaths) {
    if (insight.index() != batch.index())
      throw new IllegalArgumentException("Resultado pertence a outro lote");
    var accepted = new ArrayList<AnalysisObservation>();
    var notices = new ArrayList<>(insight.notices());
    for (var observation : insight.observations()) {
      boolean valid =
          observation.evidence().stream()
              .allMatch(ref -> !reducedPaths.contains(ref.path()) && matches(ref, batch));
      boolean clippedRisk =
          (observation.kind().equals("RISK") || observation.kind().equals("HYPOTHESIS"))
              && observation.evidence().stream()
                  .anyMatch(
                      ref ->
                          batch.excerpts().stream()
                              .anyMatch(
                                  excerpt ->
                                      excerpt.path().equals(ref.path())
                                          && !excerpt.completeFile()
                                          && (ref.startLine() == excerpt.startLine()
                                              || ref.endLine() == excerpt.endLine())));
      if (valid && clippedRisk) {
        notices.add(
            "Lote "
                + batch.index()
                + ": observação "
                + observation.id()
                + " descartada: risco apoiado em borda de fragmento parcial; requer arquivo"
                + " completo.");
        continue;
      }
      if (valid) accepted.add(observation);
      else
        notices.add(
            "Lote "
                + batch.index()
                + ": observação "
                + observation.id()
                + " descartada por referência ou trecho sem correspondência na fonte.");
    }
    return new BatchInsight(insight.index(), insight.overview(), accepted, notices);
  }

  private boolean matches(EvidenceReference ref, AnalysisBatch batch) {
    return batch.excerpts().stream()
        .anyMatch(
            excerpt -> {
              if (!excerpt.path().equals(ref.path())
                  || ref.startLine() < excerpt.startLine()
                  || ref.endLine() > excerpt.endLine()) return false;
              String[] lines = excerpt.content().replace("\r\n", "\n").split("\n", -1);
              int start = ref.startLine() - excerpt.startLine(),
                  end = ref.endLine() - excerpt.startLine();
              if (start < 0 || end >= lines.length) return false;
              String actual = String.join("\n", Arrays.copyOfRange(lines, start, end + 1));
              return actual.contains(ref.quote().replace("\r\n", "\n").strip());
            });
  }
}
