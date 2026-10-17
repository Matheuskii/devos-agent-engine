package io.devos.application;

import io.devos.domain.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

public final class AnalysisEngine {
  private final RepositoryPort repositories;
  private final PlanningPort planner;
  private final SynthesisPort writer;
  private final ReviewPort reviewer;
  private final Map<ToolId, ToolPort> tools;
  private final RunRepository runs;
  private final MemoryPort memory;
  private final Map<Role, Agent> agents;
  private final String mode;

  public AnalysisEngine(
      RepositoryPort repositories,
      PlanningPort planner,
      SynthesisPort writer,
      ReviewPort reviewer,
      List<ToolPort> tools,
      RunRepository runs,
      MemoryPort memory,
      List<Agent> agents,
      String mode) {
    this.repositories = repositories;
    this.planner = planner;
    this.writer = writer;
    this.reviewer = reviewer;
    this.runs = runs;
    this.memory = memory;
    this.mode = mode;
    var registry = new EnumMap<ToolId, ToolPort>(ToolId.class);
    for (var tool : tools)
      if (registry.put(tool.id(), tool) != null)
        throw new IllegalArgumentException("Ferramenta duplicada");
    this.tools = Map.copyOf(registry);
    var team = new EnumMap<Role, Agent>(Role.class);
    for (var agent : agents)
      if (team.put(agent.role(), agent) != null)
        throw new IllegalArgumentException("Papel duplicado");
    for (var role : Role.values())
      if (!team.containsKey(role) || !team.get(role).enabled())
        throw new IllegalArgumentException("Agente ausente/inativo: " + role);
    // A execução compartilha uma política; evita limites divergentes entre os três papéis.
    if (team.values().stream().map(Agent::policy).distinct().count() != 1)
      throw new IllegalArgumentException("Políticas da equipe devem coincidir");
    this.agents = Map.copyOf(team);
  }

  public RunRecord analyze(Path path, String objective) {
    var task = new Task(UUID.randomUUID(), objective);
    var policy = agents.get(Role.EXECUTOR).policy();
    var budget = new ExecutionBudget(policy);
    var started = Instant.now();
    long startNanos = System.nanoTime();
    var events = new ArrayList<AgentRun>();
    var notices = new ArrayList<String>();
    var results = new ArrayList<ToolResult>();
    String key = "unresolved", summary = "", feedback = "";
    int revision = 0;
    Role activeRole = Role.PLANNER;
    task.transitionTo(TaskStatus.PLANNING);
    try {
      var snapshot = repositories.read(path);
      budget.remaining();
      key = snapshot.repositoryKey();
      notices.addAll(snapshot.notices());
      var plan =
          planner.plan(agents.get(Role.PLANNER), objective, snapshot, budget.reserveModelCall());
      budget.remaining();
      plan.validateFor(agents.get(Role.EXECUTOR));
      if (!plan.steps().stream()
          .map(PlanStep::tool)
          .collect(java.util.stream.Collectors.toSet())
          .containsAll(Set.of(ToolId.INVENTORY, ToolId.QUALITY)))
        throw new IllegalArgumentException("Critérios de análise exigem INVENTORY e QUALITY");
      for (var step : plan.steps())
        if (!tools.containsKey(step.tool()))
          throw new IllegalArgumentException("Ferramenta não registrada: " + step.tool());
      events.add(
          event(Role.PLANNER, "plan", "SUCCEEDED", plan.steps().size() + " etapas validadas"));
      task.transitionTo(TaskStatus.EXECUTING);
      activeRole = Role.EXECUTOR;
      for (var step : plan.orderedSteps()) {
        budget.remaining();
        agents.get(Role.EXECUTOR).requireTool(step.tool());
        var result = tools.get(step.tool()).execute(snapshot);
        results.add(result);
        events.add(event(Role.EXECUTOR, step.id(), "SUCCEEDED", result.summary()));
      }
      String prior = "";
      try {
        prior = memory.recall(key, objective, budget.remaining());
      } catch (RuntimeException ex) {
        notices.add("Memória indisponível na consulta; análise segue com evidências atuais.");
      }
      while (true) {
        activeRole = Role.EXECUTOR;
        summary =
            Checks.text(
                writer.summarize(
                    agents.get(Role.EXECUTOR),
                    objective,
                    snapshot,
                    List.copyOf(results),
                    prior,
                    feedback,
                    budget.reserveModelCall()),
                "Síntese");
        budget.remaining();
        events.add(event(Role.EXECUTOR, "synthesis", "SUCCEEDED", "Revisão " + revision));
        task.transitionTo(TaskStatus.REVIEWING);
        activeRole = Role.REVIEWER;
        var decision =
            reviewer.review(
                agents.get(Role.REVIEWER),
                objective,
                snapshot,
                List.copyOf(results),
                summary,
                budget.reserveModelCall());
        budget.remaining();
        feedback = decision.feedback();
        events.add(
            event(
                Role.REVIEWER,
                "review",
                "SUCCEEDED",
                (decision.approved() ? "APPROVED: " : "REJECTED: ") + feedback));
        if (decision.approved()) {
          task.transitionTo(TaskStatus.COMPLETED);
          try {
            memory.remember(key, objective, summary, budget.remaining());
          } catch (RuntimeException ex) {
            notices.add("Não foi possível atualizar a memória; resultado preservado.");
          }
          break;
        }
        if (revision >= policy.maxRevisions()) {
          task.transitionTo(TaskStatus.REJECTED);
          break;
        }
        revision++;
        task.transitionTo(TaskStatus.EXECUTING);
      }
    } catch (RuntimeException ex) {
      if (task.status() != TaskStatus.COMPLETED && task.status() != TaskStatus.REJECTED)
        task.transitionTo(TaskStatus.FAILED);
      // Erros dos adaptadores são sanitizados antes de cruzar a porta.
      feedback = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
      events.add(event(activeRole, "failure", "FAILED", feedback));
    }
    var findings = results.stream().flatMap(r -> r.findings().stream()).distinct().toList();
    var record =
        new RunRecord(
            task.id().toString(),
            key,
            objective,
            task.status().name(),
            mode,
            started.toString(),
            (System.nanoTime() - startNanos) / 1_000_000,
            budget.calls(),
            revision,
            summary,
            findings,
            events,
            notices,
            feedback);
    runs.save(record);
    return record;
  }

  private AgentRun event(Role role, String operation, String status, String detail) {
    return new AgentRun(agents.get(role).id(), role, operation, status, detail);
  }
}
