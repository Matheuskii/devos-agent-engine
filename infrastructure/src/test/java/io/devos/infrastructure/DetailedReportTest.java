package io.devos.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.devos.application.*;
import io.devos.domain.*;
import java.util.*;
import org.junit.jupiter.api.*;

class DetailedReportTest {
  @org.junit.jupiter.api.io.TempDir java.nio.file.Path root;

  RunRecord record(String status) {
    var c =
        new RepositoryCoverage(
            2,
            1,
            1,
            5000,
            5000,
            2,
            status.equals("COMPLETED") ? 2 : 1,
            true,
            List.of(),
            List.of("A.java", "A test.java"));
    var report =
        status.equals("COMPLETED")
            ? new AnalysisReport(
                "Detailed summary",
                "Architecture explained",
                "One test file read; no tests executed",
                List.of("Strong point"),
                List.of("Concrete action"),
                List.of("Static analysis"))
            : null;
    var o =
        new AnalysisObservation(
            "B1-O1",
            "Potential risk",
            "RISK",
            "MEDIUM",
            "Observed code",
            "Impact on callers",
            "Check contract",
            List.of(new EvidenceReference("A.java", 4, 4, "return value;")));
    return new RunRecord(
        "run",
        "repo",
        "Analyze",
        status,
        "test",
        "now",
        1000,
        5,
        0,
        "Summary",
        List.of(),
        List.of(),
        List.of(),
        "Supported",
        c,
        report,
        List.of(new BatchInsight(1, "Batch", List.of(o), List.of())));
  }

  @Test
  void reportExplainsCoverageImpactActionAndEvidence() {
    String md = FileRunRepository.markdown(record("COMPLETED"));
    assertTrue(md.contains("Arquivos de teste e apoio"));
    assertTrue(md.contains("Arquitetura observada"));
    assertTrue(md.contains("Impact on callers"));
    assertTrue(md.contains("Check contract"));
    assertTrue(md.contains("A.java:4-4"));
    assertTrue(md.contains("return value;"));
    assertTrue(md.contains("Hipótese") || md.contains("Risco"));
  }

  @Test
  void interruptedReportIsExplicitlyPartialAndAliasesPreserveId() throws Exception {
    var r = record("FAILED");
    new FileRunRepository(root).save(r);
    String md = java.nio.file.Files.readString(root.resolve("latest.md"));
    assertTrue(md.contains("Resultado parcial"));
    assertEquals(
        java.nio.file.Files.readString(root.resolve("run.json")),
        java.nio.file.Files.readString(root.resolve("latest.json")));
    assertTrue(md.contains("1 / 2"));
  }
}
