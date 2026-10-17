package io.devos.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.devos.domain.*;
import java.util.*;
import org.junit.jupiter.api.*;

class MainTest {
  @org.junit.jupiter.api.io.TempDir java.nio.file.Path root;

  @Test
  void offlineAnalysisPersistsReportAndAuditWithEvidence() throws Exception {
    var repo = java.nio.file.Files.createDirectories(root.resolve("repo"));
    java.nio.file.Files.writeString(repo.resolve("Main.java"), "class Main { // TODO review\n}");
    var output = root.resolve("reports");
    var bytes = new java.io.ByteArrayOutputStream();
    var stream = new java.io.PrintStream(bytes);
    int exit =
        Main.run(
            new String[] {"analyze", repo.toString(), "--output", output.toString()},
            Map.of(),
            stream,
            stream);
    assertEquals(0, exit, bytes.toString());
    try (var paths = java.nio.file.Files.list(output)) {
      var files = paths.toList();
      assertEquals(2, files.size());
      var json =
          java.nio.file.Files.readString(
              files.stream().filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow());
      assertTrue(json.contains("COMPLETED"));
      assertTrue(json.contains("PENDING_MARKER"));
      assertTrue(json.contains("REVIEWER"));
      assertTrue(json.contains("offline"));
    }
  }

  @Test
  void missingCredentialsFailBeforeAnalysis() {
    assertEquals(
        2,
        Main.run(
            new String[] {"analyze", root.toString(), "--provider", "groq"},
            Map.of(),
            System.out,
            System.err));
  }

  @Test
  void failedReadIsPersistedAndReturnsNonzero() {
    var out = root.resolve("reports");
    assertEquals(
        1,
        Main.run(
            new String[] {
              "analyze", root.resolve("missing").toString(), "--output", out.toString()
            },
            Map.of(),
            System.out,
            System.err));
    assertTrue(java.nio.file.Files.isDirectory(out));
  }

  @Test
  void unknownOptionIsRejected() {
    assertEquals(
        2,
        Main.run(
            new String[] {"analyze", root.toString(), "--shell", "run"},
            Map.of(),
            System.out,
            System.err));
  }

  @Test
  void helpDoesNotRequireKeys() {
    assertEquals(0, Main.run(new String[] {"--help"}, Map.of(), System.out, System.err));
  }
}
