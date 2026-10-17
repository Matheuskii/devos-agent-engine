package io.devos.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.*;

class DomainTest {
  private Agent executor() {
    return new Agent(
        UUID.randomUUID(),
        "Executor",
        Role.EXECUTOR,
        "Analyze",
        "Execução",
        Set.of("analyze"),
        Set.of(ToolId.INVENTORY, ToolId.QUALITY),
        ExecutionPolicy.defaults(),
        true);
  }

  @Test
  void agentCopiesMutableCollections() {
    var tools = new HashSet<>(Set.of(ToolId.INVENTORY));
    var a =
        new Agent(
            UUID.randomUUID(),
            "A",
            Role.EXECUTOR,
            "P",
            "D",
            Set.of(),
            tools,
            ExecutionPolicy.defaults(),
            true);
    tools.clear();
    assertTrue(a.allowedToolIds().contains(ToolId.INVENTORY));
    assertThrows(UnsupportedOperationException.class, () -> a.allowedToolIds().clear());
  }

  @Test
  void deniesUnauthorizedTool() {
    var a =
        new Agent(
            UUID.randomUUID(),
            "P",
            Role.PLANNER,
            "P",
            "D",
            Set.of(),
            Set.of(),
            ExecutionPolicy.defaults(),
            true);
    assertThrows(IllegalStateException.class, () -> a.requireTool(ToolId.QUALITY));
  }

  @Test
  void disabledAgentCannotExecute() {
    var a =
        new Agent(
            UUID.randomUUID(),
            "A",
            Role.EXECUTOR,
            "P",
            "D",
            Set.of(),
            Set.of(ToolId.INVENTORY),
            ExecutionPolicy.defaults(),
            false);
    assertThrows(IllegalStateException.class, () -> a.requireTool(ToolId.INVENTORY));
  }

  @Test
  void rejectsCycle() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Plan(
                List.of(
                    new PlanStep("a", ToolId.INVENTORY, Set.of("b")),
                    new PlanStep("b", ToolId.QUALITY, Set.of("a")))));
  }

  @Test
  void rejectsUnknownDependency() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Plan(List.of(new PlanStep("a", ToolId.INVENTORY, Set.of("missing")))));
  }

  @Test
  void rejectsDuplicateSteps() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Plan(
                List.of(
                    new PlanStep("a", ToolId.INVENTORY, Set.of()),
                    new PlanStep("a", ToolId.QUALITY, Set.of()))));
  }

  @Test
  void sortsDependenciesBeforeDependents() {
    var p =
        new Plan(
            List.of(
                new PlanStep("b", ToolId.QUALITY, Set.of("a")),
                new PlanStep("a", ToolId.INVENTORY, Set.of())));
    assertEquals(List.of("a", "b"), p.orderedSteps().stream().map(PlanStep::id).toList());
  }

  @Test
  void genericDomainPlanAllowsSubsetOfAuthorizedTools() {
    var p = new Plan(List.of(new PlanStep("a", ToolId.INVENTORY, Set.of())));
    assertDoesNotThrow(() -> p.validateFor(executor()));
  }

  @Test
  void rejectsOversizedPlan() {
    var steps = new ArrayList<PlanStep>();
    for (int i = 0; i < 7; i++)
      steps.add(new PlanStep("s" + i, i == 0 ? ToolId.INVENTORY : ToolId.QUALITY, Set.of()));
    assertThrows(IllegalArgumentException.class, () -> new Plan(steps).validateFor(executor()));
  }

  @Test
  void terminalTaskCannotRestart() {
    var t = new Task(UUID.randomUUID(), "Analyze");
    t.transitionTo(TaskStatus.PLANNING);
    t.transitionTo(TaskStatus.EXECUTING);
    t.transitionTo(TaskStatus.REVIEWING);
    t.transitionTo(TaskStatus.COMPLETED);
    assertThrows(IllegalStateException.class, () -> t.transitionTo(TaskStatus.EXECUTING));
  }

  @Test
  void cannotSkipReview() {
    var t = new Task(UUID.randomUUID(), "A");
    t.transitionTo(TaskStatus.PLANNING);
    t.transitionTo(TaskStatus.EXECUTING);
    assertThrows(IllegalStateException.class, () -> t.transitionTo(TaskStatus.COMPLETED));
  }

  @Test
  void rejectsInvalidPolicy() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExecutionPolicy(0, 3, 0, java.time.Duration.ofSeconds(1)));
  }
}
