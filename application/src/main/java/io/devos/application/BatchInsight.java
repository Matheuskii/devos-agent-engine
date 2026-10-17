package io.devos.application;

import io.devos.domain.AnalysisObservation;
import java.util.*;

public record BatchInsight(
    int index, String overview, List<AnalysisObservation> observations, List<String> notices) {
  public BatchInsight {
    overview = io.devos.domain.Checks.text(overview, "Resumo do lote");
    observations = List.copyOf(observations);
    notices = List.copyOf(notices);
  }
}
