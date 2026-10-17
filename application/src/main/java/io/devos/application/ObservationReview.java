package io.devos.application;

import java.util.*;

public final class ObservationReview {
  public List<BatchInsight> apply(List<BatchInsight> insights, ReviewDecision decision) {
    var known =
        insights.stream()
            .flatMap(b -> b.observations().stream())
            .map(o -> o.id())
            .collect(java.util.stream.Collectors.toSet());
    if (!known.containsAll(decision.rejectedObservationIds()))
      throw new IllegalArgumentException("Revisor citou observação inexistente");
    return insights.stream()
        .map(
            batch -> {
              var notes = new ArrayList<>(batch.notices());
              var accepted =
                  batch.observations().stream()
                      .filter(
                          o -> {
                            if (!decision.rejectedObservationIds().contains(o.id())) return true;
                            notes.add(
                                "Revisão semântica descartou "
                                    + o.id()
                                    + ": "
                                    + decision.feedback());
                            return false;
                          })
                      .toList();
              return new BatchInsight(batch.index(), batch.overview(), accepted, notes);
            })
        .toList();
  }
}
