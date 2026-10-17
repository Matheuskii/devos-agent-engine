package io.devos.application;

import io.devos.domain.*;
import java.util.*;

public record ObservationAuditContext(
    AnalysisObservation observation, List<SourceExcerpt> sourceContext) {
  public ObservationAuditContext {
    sourceContext = List.copyOf(sourceContext);
  }

  public static ObservationAuditContext from(
      AnalysisObservation observation, RepositorySnapshot snapshot) {
    var excerpts = new ArrayList<SourceExcerpt>();
    for (var ref : observation.evidence()) {
      var file =
          snapshot.files().stream()
              .filter(f -> f.path().equals(ref.path()))
              .findFirst()
              .orElseThrow();
      var lines = file.content().replace("\r\n", "\n").split("\n", -1);
      int start = Math.max(1, ref.startLine() - 15),
          end = Math.min(lines.length, ref.endLine() + 15);
      excerpts.add(
          new SourceExcerpt(
              ref.path(),
              start,
              end,
              String.join("\n", Arrays.copyOfRange(lines, start - 1, end)),
              start == 1 && end == lines.length));
    }
    return new ObservationAuditContext(observation, excerpts);
  }
}
