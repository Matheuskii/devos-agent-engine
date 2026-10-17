package io.devos.application;

import static org.junit.jupiter.api.Assertions.*;

import io.devos.domain.*;
import java.util.*;
import org.junit.jupiter.api.*;

class EvidenceValidatorTest {
  AnalysisBatch batch =
      new AnalysisBatch(
          1, 1, List.of(new SourceExcerpt("A.java", 10, 12, "first\nreturn value;\nlast")));

  AnalysisObservation observation(EvidenceReference ref) {
    return new AnalysisObservation(
        "B1-O1",
        "Return",
        "OBSERVATION",
        "INFO",
        "Reads value",
        "Documents behavior",
        "Inspect callers",
        List.of(ref));
  }

  BatchInsight insight(EvidenceReference ref) {
    return new BatchInsight(1, "Overview", List.of(observation(ref)), List.of());
  }

  @Test
  void acceptsLiteralEvidenceAtOriginalLine() {
    var result =
        new EvidenceValidator()
            .validate(
                insight(new EvidenceReference("A.java", 11, 11, "return value;")), batch, Set.of());
    assertEquals(1, result.observations().size());
  }

  @Test
  void discardsFabricatedPath() {
    var result =
        new EvidenceValidator()
            .validate(
                insight(new EvidenceReference("Missing.java", 11, 11, "return value;")),
                batch,
                Set.of());
    assertTrue(result.observations().isEmpty());
    assertFalse(result.notices().isEmpty());
  }

  @Test
  void discardsQuoteAtWrongLine() {
    var result =
        new EvidenceValidator()
            .validate(
                insight(new EvidenceReference("A.java", 10, 10, "return value;")), batch, Set.of());
    assertTrue(result.observations().isEmpty());
  }

  @Test
  void reducedDataCannotPretendToBeOriginalLineEvidence() {
    var result =
        new EvidenceValidator()
            .validate(
                insight(new EvidenceReference("A.java", 11, 11, "return value;")),
                batch,
                Set.of("A.java"));
    assertTrue(result.observations().isEmpty());
  }

  @Test
  void acceptsMultilineEvidenceButRejectsAnotherBatch() {
    assertEquals(
        1,
        new EvidenceValidator()
            .validate(
                insight(new EvidenceReference("A.java", 10, 11, "first\nreturn value;")),
                batch,
                Set.of())
            .observations()
            .size());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new EvidenceValidator()
                .validate(
                    new BatchInsight(2, "Wrong batch", List.of(), List.of()), batch, Set.of()));
  }

  @Test
  void riskAtClippedBoundaryCannotBecomeAnAcceptedFinding() {
    var observation =
        new AnalysisObservation(
            "B1-O1",
            "Incomplete method",
            "RISK",
            "MEDIUM",
            "Method seems incomplete",
            "Potential compilation error",
            "Read the complete file",
            List.of(new EvidenceReference("A.java", 12, 12, "last")));
    var result =
        new EvidenceValidator()
            .validate(
                new BatchInsight(1, "Fragment", List.of(observation), List.of()), batch, Set.of());
    assertTrue(result.observations().isEmpty());
    assertTrue(result.notices().getFirst().contains("fragmento parcial"));
  }
}
