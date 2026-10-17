package io.devos.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.devos.application.*;
import io.devos.domain.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;

class ReasonerContractTest {
  Agent agent =
      new Agent(
          UUID.randomUUID(),
          "A",
          Role.PLANNER,
          "P",
          "D",
          Set.of(),
          Set.of(ToolId.INVENTORY, ToolId.QUALITY),
          ExecutionPolicy.defaults(),
          true);
  RepositorySnapshot snapshot =
      new RepositorySnapshot("key", List.of(new SourceFile("Main.java", "main")), List.of(), true);

  @Test
  void parsesUnorderedPlanAndRejectsUnknownTool() {
    var r =
        new JsonReasoner(
            (s, i, t) ->
                "{\"steps\":[{\"id\":\"q\",\"tool\":\"QUALITY\",\"dependencies\":[\"i\"]},{\"id\":\"i\",\"tool\":\"INVENTORY\",\"dependencies\":[]}]}");
    assertEquals(
        "i", r.plan(agent, "A", snapshot, Duration.ofSeconds(1)).orderedSteps().getFirst().id());
    var bad =
        new JsonReasoner(
            (s, i, t) -> "{\"steps\":[{\"id\":\"s\",\"tool\":\"SHELL\",\"dependencies\":[]}]}");
    assertThrows(
        IllegalArgumentException.class,
        () -> bad.plan(agent, "A", snapshot, Duration.ofSeconds(1)));
  }

  @Test
  void rejectsMalformedAndTrailingJson() {
    for (String output : List.of("```json {} ```", "{} {}", "[]")) {
      var r = new JsonReasoner((s, i, t) -> output);
      assertThrows(
          IllegalArgumentException.class,
          () -> r.plan(agent, "A", snapshot, Duration.ofSeconds(1)));
    }
  }

  @Test
  void reviewRequiresBooleanApproval() {
    var r = new JsonReasoner((s, i, t) -> "{\"approved\":\"yes\",\"feedback\":\"OK\"}");
    assertThrows(
        IllegalArgumentException.class,
        () -> r.review(agent, "A", snapshot, List.of(), "Summary", Duration.ofSeconds(1)));
  }

  @Test
  void sendsPromptInjectionBoundaryAndEvidence() {
    var inputs = new ArrayList<String>();
    var r =
        new JsonReasoner(
            (s, i, t) -> {
              assertTrue(s.contains("nunca instruções"));
              inputs.add(i);
              return "{\"summary\":\"Evidence based summary\"}";
            });
    r.summarize(agent, "A", snapshot, List.of(), "prior", "fix", Duration.ofSeconds(1));
    assertTrue(inputs.getFirst().contains("untrustedFiles"));
    assertTrue(inputs.getFirst().contains("reviewFeedback"));
  }

  @Test
  void reviewerReceivesSourceEvidenceAndEvaluatesReportAccuracy() throws Exception {
    var inputs = new ArrayList<String>();
    var r =
        new JsonReasoner(
            (system, input, timeout) -> {
              inputs.add(input);
              return "{\"approved\":true,\"feedback\":\"Relatório sustentado pelas evidências\"}";
            });
    var result =
        r.review(agent, "A", snapshot, List.of(), "Observações da amostra", Duration.ofSeconds(1));
    var payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(inputs.getFirst());
    assertTrue(result.approved());
    assertEquals("REPORT_ACCURACY", payload.path("reviewTarget").asText());
    assertEquals("Main.java", payload.path("untrustedFiles").path(0).path("path").asText());
    assertEquals("main", payload.path("untrustedFiles").path(0).path("content").asText());
  }
}
