package io.devos.application;

import io.devos.domain.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

public final class FullAnalysisEngine {
  private final RepositoryPort repositories;
  private final PlanningPort planner;
  private final BatchInspectionPort inspector;
  private final ReportSynthesisPort writer;
  private final ReportReviewPort reviewer;
  private final RunRepository runs;
  private final MemoryPort memory;
  private final Map<Role, Agent> team;
  private final Map<ToolId, ToolPort> tools;
  private final BatchSettings settings;
  private final ProgressListener progress;
  private final String mode;

  public FullAnalysisEngine(
      RepositoryPort repositories,
      PlanningPort planner,
      BatchInspectionPort inspector,
      ReportSynthesisPort writer,
      ReportReviewPort reviewer,
      List<ToolPort> tools,
      RunRepository runs,
      MemoryPort memory,
      List<Agent> agents,
      BatchSettings settings,
      ProgressListener progress,
      String mode) {
    this.repositories = repositories;
    this.planner = planner;
    this.inspector = inspector;
    this.writer = writer;
    this.reviewer = reviewer;
    this.runs = runs;
    this.memory = memory;
    this.settings = settings;
    this.progress = progress;
    this.mode = mode;
    var teamMap = new EnumMap<Role, Agent>(Role.class);
    for (var a : agents)
      if (teamMap.put(a.role(), a) != null) throw new IllegalArgumentException("Papel duplicado");
    for (var role : Role.values())
      if (!teamMap.containsKey(role) || !teamMap.get(role).enabled())
        throw new IllegalArgumentException("Agente ausente ou inativo");
    if (teamMap.values().stream().map(Agent::policy).distinct().count() != 1)
      throw new IllegalArgumentException("Políticas divergentes");
    this.team = Map.copyOf(teamMap);
    var toolMap = new EnumMap<ToolId, ToolPort>(ToolId.class);
    for (var t : tools)
      if (toolMap.put(t.id(), t) != null)
        throw new IllegalArgumentException("Ferramenta duplicada");
    this.tools = Map.copyOf(toolMap);
  }

  public RunRecord analyze(Path root, String objective) {
    var task = new Task(UUID.randomUUID(), objective);
    var budget = new ExecutionBudget(team.get(Role.EXECUTOR).policy());
    var started = Instant.now();
    long startedNanos = System.nanoTime();
    var events = new ArrayList<AgentRun>();
    var notices = new ArrayList<String>();
    var results = new ArrayList<ToolResult>();
    var insights = new ArrayList<BatchInsight>();
    RepositorySnapshot snapshot = null;
    BatchPlan partition = null;
    AnalysisReport report = null;
    String feedback = "", key = "unresolved";
    int revisions = 0;
    Role active = Role.PLANNER;
    task.transitionTo(TaskStatus.PLANNING);
    try {
      progress.update("Lendo todos os arquivos elegíveis e preparando os lotes...");
      snapshot = repositories.read(root);
      budget.remaining();
      key = snapshot.repositoryKey();
      notices.addAll(snapshot.notices());
      partition = new BatchPlanner().partition(snapshot, settings.charactersPerBatch());
      notices.addAll(partition.reductions());
      progress.update(
          "Coleta: "
              + snapshot.files().size()
              + " arquivos; "
              + partition.originalCharacters()
              + " caracteres lidos; "
              + partition.batches().size()
              + " lotes de IA.");
      if (partition.batches().size() > settings.maxBatches())
        throw new IllegalArgumentException(
            "São necessários "
                + partition.batches().size()
                + " lotes; aumente --max-batches ou reduza o escopo. Nenhuma chamada de IA foi"
                + " feita.");
      if (partition.batches().size() + 3 > team.get(Role.EXECUTOR).policy().maxModelCalls())
        throw new IllegalArgumentException(
            "Orçamento insuficiente: exige ao menos "
                + (partition.batches().size() + 3)
                + " chamadas. Ajuste --max-calls.");
      var plan =
          planner.plan(team.get(Role.PLANNER), objective, snapshot, budget.reserveModelCall());
      plan.validateFor(team.get(Role.EXECUTOR));
      if (!plan.steps().stream()
          .map(PlanStep::tool)
          .collect(java.util.stream.Collectors.toSet())
          .containsAll(Set.of(ToolId.INVENTORY, ToolId.QUALITY)))
        throw new IllegalArgumentException("Inventário e qualidade são obrigatórios");
      events.add(
          event(
              Role.PLANNER,
              "plan",
              "Plano validado; análise em " + partition.batches().size() + " lotes"));
      task.transitionTo(TaskStatus.EXECUTING);
      active = Role.EXECUTOR;
      var executed = new HashSet<ToolId>();
      for (var step : plan.orderedSteps()) {
        team.get(Role.EXECUTOR).requireTool(step.tool());
        if (!tools.containsKey(step.tool()))
          throw new IllegalArgumentException("Ferramenta não registrada");
        if (executed.add(step.tool())) {
          budget.remaining();
          var result = tools.get(step.tool()).execute(snapshot);
          results.add(result);
          events.add(event(Role.EXECUTOR, step.id(), result.summary()));
        }
      }
      var collected = snapshot;
      var reducedPaths =
          partition.reducedFiles().stream()
              .filter(f -> partitionReduced(collected, f))
              .map(SourceFile::path)
              .collect(java.util.stream.Collectors.toSet());
      String prior = "";
      try {
        prior = memory.recall(key, objective, budget.remaining());
      } catch (RuntimeException ex) {
        notices.add("Memória indisponível; análise usa as evidências atuais.");
      }
      for (var batch : partition.batches()) {
        if (!insights.isEmpty() && !settings.delay().isZero()) pause(settings.delay(), budget);
        progress.update(
            "Analisando lote "
                + batch.index()
                + "/"
                + batch.total()
                + " ("
                + batch.excerpts().size()
                + " trechos; "
                + batch.characters()
                + " caracteres)...");
        var raw =
            inspector.inspect(
                team.get(Role.EXECUTOR),
                objective,
                batch,
                List.copyOf(results),
                budget.reserveModelCall());
        budget.remaining();
        var checked = new EvidenceValidator().validate(raw, batch, reducedPaths);
        insights.add(checked);
        notices.addAll(checked.notices());
        events.add(
            event(
                Role.EXECUTOR,
                "batch-" + batch.index(),
                checked.observations().size() + " observações com evidências verificadas"));
        // Checkpoint legível e auditável: uma falha posterior não apaga o trabalho concluído.
        runs.save(
            record(
                task,
                key,
                objective,
                started,
                startedNanos,
                budget,
                revisions,
                report,
                results,
                events,
                notices,
                feedback,
                snapshot,
                partition,
                insights));
      }
      while (true) {
        progress.update("Consolidando relatório detalhado...");
        active = Role.EXECUTOR;
        var coverage = RepositoryCoverage.of(snapshot, partition, insights.size());
        report =
            writer.synthesize(
                team.get(Role.EXECUTOR),
                objective,
                coverage,
                List.copyOf(insights),
                List.copyOf(results),
                prior,
                feedback,
                budget.reserveModelCall());
        budget.remaining();
        task.transitionTo(TaskStatus.REVIEWING);
        active = Role.REVIEWER;
        progress.update("Revisando o relatório e a sustentação das conclusões...");
        var review =
            reviewer.reviewReport(
                team.get(Role.REVIEWER),
                objective,
                coverage,
                List.copyOf(insights),
                List.copyOf(results),
                report,
                budget.reserveModelCall());
        budget.remaining();
        feedback = review.feedback();
        events.add(
            event(
                Role.REVIEWER,
                "review",
                (review.approved() ? "APPROVED: " : "REJECTED: ") + feedback));
        if (review.approved()) {
          task.transitionTo(TaskStatus.COMPLETED);
          try {
            memory.remember(key, objective, report.executiveSummary(), budget.remaining());
          } catch (RuntimeException ex) {
            notices.add("Não foi possível atualizar a memória; relatório preservado.");
          }
          break;
        }
        if (revisions >= team.get(Role.EXECUTOR).policy().maxRevisions()) {
          task.transitionTo(TaskStatus.REJECTED);
          break;
        }
        revisions++;
        task.transitionTo(TaskStatus.EXECUTING);
      }
    } catch (RuntimeException ex) {
      if (task.status() != TaskStatus.COMPLETED && task.status() != TaskStatus.REJECTED)
        task.transitionTo(TaskStatus.FAILED);
      feedback = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
      events.add(new AgentRun(team.get(active).id(), active, "failure", "FAILED", feedback));
      progress.update(
          "Execução interrompida. Os lotes já concluídos e a cobertura serão preservados.");
    }
    var result =
        record(
            task,
            key,
            objective,
            started,
            startedNanos,
            budget,
            revisions,
            report,
            results,
            events,
            notices,
            feedback,
            snapshot,
            partition,
            insights);
    runs.save(result);
    return result;
  }

  private static boolean partitionReduced(RepositorySnapshot snapshot, SourceFile reduced) {
    return snapshot.files().stream()
        .filter(f -> f.path().equals(reduced.path()))
        .findFirst()
        .map(f -> !f.content().equals(reduced.content()))
        .orElse(false);
  }

  private void pause(Duration delay, ExecutionBudget budget) {
    budget.remaining();
    try {
      Thread.sleep(delay.toMillis());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Execução interrompida durante intervalo entre lotes");
    }
    budget.remaining();
  }

  private AgentRun event(Role role, String operation, String detail) {
    return new AgentRun(team.get(role).id(), role, operation, "SUCCEEDED", detail);
  }

  private RunRecord record(
      Task task,
      String key,
      String objective,
      Instant started,
      long nanos,
      ExecutionBudget budget,
      int revisions,
      AnalysisReport report,
      List<ToolResult> results,
      List<AgentRun> events,
      List<String> notices,
      String feedback,
      RepositorySnapshot snapshot,
      BatchPlan partition,
      List<BatchInsight> insights) {
    var coverage =
        snapshot == null || partition == null
            ? null
            : RepositoryCoverage.of(snapshot, partition, insights.size());
    return new RunRecord(
        task.id().toString(),
        key,
        objective,
        task.status().name(),
        mode,
        started.toString(),
        (System.nanoTime() - nanos) / 1000000,
        budget.calls(),
        revisions,
        report == null
            ? "Relatório em preparação. Lotes concluídos: " + insights.size() + "."
            : report.executiveSummary(),
        results.stream().flatMap(t -> t.findings().stream()).distinct().toList(),
        events,
        notices,
        feedback,
        coverage,
        report,
        insights);
  }
}
