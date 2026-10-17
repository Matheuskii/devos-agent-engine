package io.devos.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.devos.application.*;
import io.devos.domain.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;

class BatchReasonerTest {
  Agent agent =
      new Agent(
          UUID.randomUUID(),
          "A",
          Role.EXECUTOR,
          "prompt",
          "description",
          Set.of(),
          Set.of(),
          ExecutionPolicy.defaults(),
          true);
  AnalysisBatch batch =
      new AnalysisBatch(1, 1, List.of(new SourceExcerpt("A.java", 10, 10, "return value;")));

  @Test
  void inspectorReceivesOriginalLineNumbersAndParsesEvidence() {
    var inputs = new ArrayList<String>();
    var reasoner =
        new JsonBatchReasoner(
            (s, i, t) -> {
              inputs.add(i);
              return "{\"overview\":\"A return expression\",\"observations\":[{\"title\":\"Return"
                         + " value\",\"kind\":\"OBSERVATION\",\"priority\":\"INFO\",\"explanation\":\"Returns"
                         + " a value\",\"impact\":\"Caller receives"
                         + " value\",\"recommendation\":\"Inspect"
                         + " callers\",\"evidence\":[{\"path\":\"A.java\",\"startLine\":10,\"endLine\":10,\"quote\":\"return"
                         + " value;\"}]}]}";
            });
    var insight = reasoner.inspect(agent, "Analyze", batch, List.of(), Duration.ofSeconds(1));
    assertEquals("B1-O1", insight.observations().getFirst().id());
    assertTrue(inputs.getFirst().contains("10 | return value;"));
    assertEquals(
        1, new EvidenceValidator().validate(insight, batch, Set.of()).observations().size());
  }

  @Test
  void malformedObservationIsNotAcceptedAsFinding() {
    var reasoner =
        new JsonBatchReasoner(
            (s, i, t) ->
                "{\"overview\":\"Batch\",\"observations\":[{\"title\":\"Unsupported\",\"evidence\":[]}]}");
    var result = reasoner.inspect(agent, "A", batch, List.of(), Duration.ofSeconds(1));
    assertTrue(result.observations().isEmpty());
    assertFalse(result.notices().isEmpty());
  }

  @Test
  void synthesisRequiresStructuredSections() {
    var reasoner = new JsonBatchReasoner((s, i, t) -> "{\"summary\":\"Tiny generic summary\"}");
    var coverage =
        new RepositoryCoverage(1, 1, 0, 20, 20, 1, 1, true, List.of(), List.of("A.java"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            reasoner.synthesize(
                agent, "A", coverage, List.of(), List.of(), "", "", Duration.ofSeconds(1)));
  }
}
