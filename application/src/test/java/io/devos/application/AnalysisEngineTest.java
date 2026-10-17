package io.devos.application;

import static org.junit.jupiter.api.Assertions.*;

import io.devos.domain.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;

class AnalysisEngineTest {
  final RepositorySnapshot snapshot =
      new RepositorySnapshot(
          "repo", List.of(new SourceFile("Main.java", "class Main {}")), List.of(), true);
  final List<RunRecord> saved = new ArrayList<>();
  ExecutionPolicy policy = ExecutionPolicy.defaults();

  Plan standardPlan() {
    return new Plan(
        List.of(
            new PlanStep("inventory", ToolId.INVENTORY, Set.of()),
            new PlanStep("quality", ToolId.QUALITY, Set.of("inventory"))));
  }

  List<Agent> agents() {
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
                    policy,
                    true))
        .toList();
  }

  ToolPort tool(ToolId id) {
    return new ToolPort() {
      public ToolId id() {
        return id;
      }

      public ToolResult execute(RepositorySnapshot s) {
        return new ToolResult(id, "Evidence " + id, List.of());
      }
    };
  }

  MemoryPort memory =
      new MemoryPort() {
        public String recall(String k, String o, Duration t) {
          return "";
        }

        public void remember(String k, String o, String s, Duration t) {}
      };

  AnalysisEngine engine(PlanningPort p, SynthesisPort s, ReviewPort r) {
    return new AnalysisEngine(
        path -> snapshot,
        p,
        s,
        r,
        List.of(tool(ToolId.INVENTORY), tool(ToolId.QUALITY)),
        saved::add,
        memory,
        agents(),
        "test");
  }

  PlanningPort planner = (a, o, s, t) -> standardPlan();
  SynthesisPort writer = (a, o, s, e, m, f, t) -> "Sustained summary";

  @Test
  void completesWithThreeRolesAndEvidenceBeforeReview() {
    var seen = new ArrayList<ToolResult>();
    var result =
        engine(
                planner,
                writer,
                (a, o, snapshotArg, e, s, t) -> {
                  assertSame(snapshot, snapshotArg);
                  seen.addAll(e);
                  return new ReviewDecision(true, "Evidence confirmed");
                })
            .analyze(Path.of("."), "Analyze");
    assertEquals("COMPLETED", result.status());
    assertEquals(3, result.modelCalls());
    assertEquals(2, seen.size());
    assertEquals(1, saved.size());
    assertEquals(
        Set.of(Role.PLANNER, Role.EXECUTOR, Role.REVIEWER),
        result.events().stream().map(AgentRun::role).collect(java.util.stream.Collectors.toSet()));
  }

  @Test
  void rejectionLeadsToBoundedRevisionAndFeedback() {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var feedbacks = new ArrayList<String>();
    var result =
        engine(
                planner,
                (a, o, s, e, m, f, t) -> {
                  feedbacks.add(f);
                  return "Summary";
                },
                (a, o, snapshotArg, e, s, t) ->
                    new ReviewDecision(calls.incrementAndGet() > 1, "Fix unsupported claim"))
            .analyze(Path.of("."), "Analyze");
    assertEquals("COMPLETED", result.status());
    assertEquals(1, result.revisions());
    assertEquals(5, result.modelCalls());
    assertEquals("Fix unsupported claim", feedbacks.get(1));
  }

  @Test
  void permanentRejectionStopsAfterConfiguredLimit() {
    var result =
        engine(
                planner,
                writer,
                (a, o, snapshotArg, e, s, t) -> new ReviewDecision(false, "Unsupported"))
            .analyze(Path.of("."), "Analyze");
    assertEquals("REJECTED", result.status());
    assertEquals(7, result.modelCalls());
    assertEquals(2, result.revisions());
  }

  @Test
  void invalidPlanFailsBeforeToolsExecute() {
    var result =
        engine(
                (a, o, s, t) -> new Plan(List.of(new PlanStep("one", ToolId.INVENTORY, Set.of()))),
                writer,
                (a, o, snapshotArg, e, s, t) -> new ReviewDecision(true, "OK"))
            .analyze(Path.of("."), "Analyze");
    assertEquals("FAILED", result.status());
    assertEquals(1, result.modelCalls());
    assertEquals(1, saved.size());
  }

  @Test
  void providerFailurePersistsFailedRun() {
    var result =
        engine(
                (a, o, s, t) -> {
                  throw new IllegalStateException("HTTP 429");
                },
                writer,
                (a, o, snapshotArg, e, s, t) -> new ReviewDecision(true, "OK"))
            .analyze(Path.of("."), "Analyze");
    assertEquals("FAILED", result.status());
    assertTrue(saved.getFirst().review().contains("429"));
  }

  @Test
  void memoryFailureDoesNotDiscardAnalysis() {
    memory =
        new MemoryPort() {
          public String recall(String k, String o, Duration t) {
            throw new IllegalStateException();
          }

          public void remember(String k, String o, String s, Duration t) {
            throw new IllegalStateException();
          }
        };
    var result =
        engine(planner, writer, (a, o, snapshotArg, e, s, t) -> new ReviewDecision(true, "OK"))
            .analyze(Path.of("."), "Analyze");
    assertEquals("COMPLETED", result.status());
    assertEquals(2, result.notices().size());
  }

  @Test
  void rejectedSummaryIsNotStoredInMemory() {
    var stored = new java.util.concurrent.atomic.AtomicInteger();
    memory =
        new MemoryPort() {
          public String recall(String k, String o, Duration t) {
            return "";
          }

          public void remember(String k, String o, String s, Duration t) {
            stored.incrementAndGet();
          }
        };
    engine(planner, writer, (a, o, snapshotArg, e, s, t) -> new ReviewDecision(false, "Bad"))
        .analyze(Path.of("."), "Analyze");
    assertEquals(0, stored.get());
  }

  @Test
  void callBudgetPreventsAdditionalProviderRequest() {
    policy = new ExecutionPolicy(6, 3, 2, Duration.ofMinutes(1));
    var result =
        engine(planner, writer, (a, o, snapshotArg, e, s, t) -> new ReviewDecision(false, "Bad"))
            .analyze(Path.of("."), "Analyze");
    assertEquals("FAILED", result.status());
    assertEquals(3, result.modelCalls());
    assertTrue(result.review().contains("Limite"));
  }

  @Test
  void deadlineUsesMonotonicClock() {
    var nanos = new java.util.concurrent.atomic.AtomicLong();
    var budget =
        new ExecutionBudget(new ExecutionPolicy(3, 3, 0, Duration.ofNanos(10)), nanos::get);
    assertEquals(10, budget.remaining().toNanos());
    nanos.set(10);
    assertThrows(IllegalStateException.class, budget::remaining);
  }

  @Test
  void persistenceFailurePropagates() {
    var e =
        new AnalysisEngine(
            path -> snapshot,
            planner,
            writer,
            (a, o, snapshotArg, x, s, t) -> new ReviewDecision(true, "OK"),
            List.of(tool(ToolId.INVENTORY), tool(ToolId.QUALITY)),
            r -> {
              throw new IllegalStateException("Disk full");
            },
            memory,
            agents(),
            "test");
    assertThrows(IllegalStateException.class, () -> e.analyze(Path.of("."), "Analyze"));
  }
}
