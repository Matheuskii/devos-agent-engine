package io.devos.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.devos.application.*;
import io.devos.domain.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;

class ObservationAuditorTest {
  Agent reviewer =
      new Agent(
          UUID.randomUUID(),
          "R",
          Role.REVIEWER,
          "P",
          "D",
          Set.of(),
          Set.of(),
          ExecutionPolicy.defaults(),
          true);

  ObservationAuditContext context() {
    var o =
        new AnalysisObservation(
            "B1-O1",
            "Flag",
            "RISK",
            "MEDIUM",
            "Activates flag",
            "State risk",
            "Check",
            List.of(new EvidenceReference("A.java", 2, 2, "setFlag(false);")));
    return ObservationAuditContext.from(
        o,
        new RepositorySnapshot(
            "r",
            List.of(new SourceFile("A.java", "class A {\nsetFlag(false);\n}")),
            List.of(),
            true));
  }

  @Test
  void focusedAuditorReceivesAdjacentSourceAndCanRejectContradiction() {
    var auditor =
        new JsonObservationAuditor(
            (s, i, t) -> {
              assertTrue(s.contains("setFlag(false)"));
              assertTrue(i.contains("2 | setFlag(false);"));
              return "{\"decisions\":[{\"id\":\"B1-O1\",\"verdict\":\"UNSUPPORTED\",\"reason\":\"false"
                         + " disables flag\"}]}";
            });
    var r = auditor.audit(reviewer, List.of(context()), Duration.ofSeconds(1));
    assertFalse(r.approved());
    assertEquals(Set.of("B1-O1"), r.rejectedObservationIds());
  }

  @Test
  void omittedVerdictIsAnExplicitFailure() {
    var auditor = new JsonObservationAuditor((s, i, t) -> "{\"decisions\":[]}");
    assertThrows(
        IllegalArgumentException.class,
        () -> auditor.audit(reviewer, List.of(context()), Duration.ofSeconds(1)));
  }

  @Test
  void inconclusiveRiskIsNotPromotedToSupported() {
    var auditor =
        new JsonObservationAuditor(
            (s, i, t) ->
                "{\"decisions\":[{\"id\":\"B1-O1\",\"verdict\":\"INCONCLUSIVE\",\"reason\":\"Need"
                    + " wider context\"}]}");
    assertFalse(auditor.audit(reviewer, List.of(context()), Duration.ofSeconds(1)).approved());
  }
}
