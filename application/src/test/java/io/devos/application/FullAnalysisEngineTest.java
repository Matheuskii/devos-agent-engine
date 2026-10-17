package io.devos.application;

import static org.junit.jupiter.api.Assertions.*;

import io.devos.domain.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;

class FullAnalysisEngineTest {
  List<RunRecord> saved = new ArrayList<>();
  int inspections, toolCalls, syntheses;
  int maxCalls = 30;
  int maxBatches = 20;
  int reviewCalls;
  RepositorySnapshot snapshot =
      new RepositorySnapshot(
          "repo",
          List.of(
              new SourceFile("src/main/A.java", "class A {}\n".repeat(600)),
              new SourceFile("src/test/A test.java", "test\n".repeat(100))),
          List.of(),
          true);

  List<Agent> agents() {
    var p = new ExecutionPolicy(6, maxCalls, 1, Duration.ofMinutes(1));
    return Arrays.stream(Role.values())
        .map(
            r ->
                new Agent(
                    UUID.randomUUID(),
                    r.name(),
                    r,
                    "prompt",
                    "description",
                    Set.of(),
                    r == Role.EXECUTOR ? Set.of(ToolId.INVENTORY, ToolId.QUALITY) : Set.of(),
                    p,
                    true))
        .toList();
  }

  PlanningPort planner =
      (a, o, s, t) ->
          new Plan(
              List.of(
                  new PlanStep("inventory", ToolId.INVENTORY, Set.of()),
                  new PlanStep("quality", ToolId.QUALITY, Set.of())));
  BatchInspectionPort inspect =
      (a, o, b, e, t) -> {
        inspections++;
        return new BatchInsight(b.index(), "Observed batch", List.of(), List.of());
      };
  ReportSynthesisPort synthesis =
      (a, o, c, i, e, m, f, t) -> {
        syntheses++;
        return new AnalysisReport(
            "Summary",
            "Architecture",
            "Tests were read, not executed",
            List.of("Evidence"),
            List.of("Inspect"),
            List.of("Static"));
      };
  MemoryPort memory =
      new MemoryPort() {
        public String recall(String k, String o, Duration t) {
          return "";
        }

        public void remember(String k, String o, String s, Duration t) {}
      };

  ToolPort tool(ToolId id) {
    return new ToolPort() {
      public ToolId id() {
        return id;
      }

      public ToolResult execute(RepositorySnapshot s) {
        toolCalls++;
        return new ToolResult(id, "Observed", List.of());
      }
    };
  }

  FullAnalysisEngine engine(ReportReviewPort review) {
    return new FullAnalysisEngine(
        p -> snapshot,
        planner,
        inspect,
        synthesis,
        review,
        List.of(tool(ToolId.INVENTORY), tool(ToolId.QUALITY)),
        saved::add,
        memory,
        agents(),
        new BatchSettings(2000, maxBatches, Duration.ZERO),
        ProgressListener.silent(),
        "test");
  }

  @Test
  void processesEntireSourceAndTestInventoryWithCheckpoints() {
    var r =
        engine((a, o, c, i, e, report, t) -> new ReviewDecision(true, "Supported"))
            .analyze(Path.of("."), "Analyze");
    assertEquals("COMPLETED", r.status());
    assertEquals(r.coverage().plannedBatches(), r.coverage().completedBatches());
    assertTrue(r.coverage().processingComplete());
    assertEquals(1, r.coverage().testFiles());
    assertEquals(r.coverage().plannedBatches(), inspections);
    assertEquals(inspections + 1, saved.size());
    assertTrue(saved.stream().anyMatch(x -> x.status().equals("EXECUTING")));
    assertEquals(inspections + 3, r.modelCalls());
  }

  @Test
  void batchCapFailsBeforeAnyApiRequest() {
    maxBatches = 1;
    var r =
        engine((a, o, c, i, e, report, t) -> new ReviewDecision(true, "OK"))
            .analyze(Path.of("."), "Analyze");
    assertEquals("FAILED", r.status());
    assertEquals(0, r.modelCalls());
    assertEquals(0, inspections);
  }

  @Test
  void insufficientBudgetIsDetectedBeforePlanning() {
    maxCalls = 3;
    var r =
        engine((a, o, c, i, e, report, t) -> new ReviewDecision(true, "OK"))
            .analyze(Path.of("."), "Analyze");
    assertEquals("FAILED", r.status());
    assertEquals(0, r.modelCalls());
    assertTrue(r.review().contains("Orçamento"));
  }

  @Test
  void failedBatchPreservesEarlierEvidenceAndPartialCoverage() {
    inspect =
        (a, o, b, e, t) -> {
          inspections++;
          if (b.index() == 3) throw new IllegalStateException("HTTP 429");
          return new BatchInsight(b.index(), "Read", List.of(), List.of());
        };
    var r =
        engine((a, o, c, i, e, report, t) -> new ReviewDecision(true, "OK"))
            .analyze(Path.of("."), "Analyze");
    assertEquals("FAILED", r.status());
    assertEquals(2, r.batches().size());
    assertEquals(2, r.coverage().completedBatches());
    assertFalse(r.coverage().processingComplete());
    assertEquals(0, syntheses);
    assertTrue(r.review().contains("429"));
  }

  @Test
  void reviewRevisionDoesNotRepeatRepositoryBatches() {
    var r =
        engine(
                (a, o, c, i, e, report, t) ->
                    new ReviewDecision(++reviewCalls > 1, "Clarify impact"))
            .analyze(Path.of("."), "Analyze");
    assertEquals("COMPLETED", r.status());
    assertEquals(1, r.revisions());
    assertEquals(r.coverage().plannedBatches(), inspections);
    assertEquals(2, syntheses);
  }

  @Test
  void duplicatePlanToolsAreNotExecutedRepeatedly() {
    planner =
        (a, o, s, t) ->
            new Plan(
                List.of(
                    new PlanStep("one", ToolId.INVENTORY, Set.of()),
                    new PlanStep("two", ToolId.INVENTORY, Set.of()),
                    new PlanStep("three", ToolId.QUALITY, Set.of())));
    var r =
        engine((a, o, c, i, e, report, t) -> new ReviewDecision(true, "OK"))
            .analyze(Path.of("."), "Analyze");
    assertEquals("COMPLETED", r.status());
    assertEquals(2, toolCalls);
  }
}
