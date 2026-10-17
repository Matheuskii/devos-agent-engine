package io.devos.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.devos.application.SourceFile;
import io.devos.domain.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;

class LocalRepositoryTest {
  @org.junit.jupiter.api.io.TempDir Path root;

  void put(String path, String content) throws Exception {
    Path p = root.resolve(path);
    Files.createDirectories(p.getParent());
    Files.writeString(p, content);
  }

  @Test
  void ignoresSecretsDependenciesAndSymlinks() throws Exception {
    put("Main.java", "class Main {}");
    put(".env", "password=private");
    put("credentials.json", "secret");
    put("node_modules/a.js", "secret");
    Path external = Files.createTempFile("outside-", ".java");
    try {
      Files.writeString(external, "private");
      Files.createSymbolicLink(root.resolve("linked.java"), external);
      var s = new LocalRepository().read(root);
      assertEquals(List.of("Main.java"), s.files().stream().map(SourceFile::path).toList());
    } finally {
      Files.deleteIfExists(external);
    }
  }

  @Test
  void respectsExplicitIgnoreGlob() throws Exception {
    put("Main.java", "main");
    put("private/data.txt", "data");
    put(".devosignore", "private");
    assertEquals(1, new LocalRepository().read(root).files().size());
  }

  @Test
  void redactsCredentialAssignmentsAndTokens() throws Exception {
    put("config.properties", "api_key=do-not-send\nname=example\npassword: abc");
    var text = new LocalRepository().read(root).files().getFirst().content();
    assertFalse(text.contains("do-not-send"));
    assertFalse(text.contains("abc"));
    assertTrue(text.contains("name=example"));
  }

  @Test
  void truncationIsExplicit() throws Exception {
    put("large.txt", "x".repeat(200));
    var s = new LocalRepository(2, 50, 20).read(root);
    assertFalse(s.complete());
    assertEquals(20, s.files().getFirst().content().length());
    assertFalse(s.notices().isEmpty());
  }

  @Test
  void rejectsEmptyOrInvalidRepository() {
    assertThrows(IllegalArgumentException.class, () -> new LocalRepository().read(root));
    assertThrows(
        IllegalArgumentException.class, () -> new LocalRepository().read(root.resolve("missing")));
  }

  @Test
  void reportsExactEvidenceLineWithoutCallingItBug() throws Exception {
    put("Main.java", "class Main {\n // TODO handle errors\n}");
    var s = new LocalRepository().read(root);
    var finding =
        new QualityTool()
            .execute(s).findings().stream()
                .filter(f -> f.rule().equals("PENDING_MARKER"))
                .findFirst()
                .orElseThrow();
    assertEquals(2, finding.line());
    assertEquals("Main.java", finding.path());
    assertTrue(finding.evidence().contains("TODO"));
    assertEquals("INFO", finding.severity());
  }

  @Test
  void recognizesTestsAndReadme() throws Exception {
    put("README.md", "Use it");
    put("src/test/java/EngineTest.java", "class EngineTest {}");
    var findings = new QualityTool().execute(new LocalRepository().read(root)).findings();
    assertTrue(findings.isEmpty());
  }

  @Test
  void snapshotKeySeparatesRepositories() throws Exception {
    put("Main.java", "main");
    Path other = Files.createDirectory(root.resolve("other"));
    Files.writeString(other.resolve("Other.java"), "other");
    assertNotEquals(
        new LocalRepository().read(root).repositoryKey(),
        new LocalRepository().read(other).repositoryKey());
  }
}
