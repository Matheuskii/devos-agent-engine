package io.devos.application;

import static org.junit.jupiter.api.Assertions.*;

import io.devos.domain.*;
import java.util.*;
import org.junit.jupiter.api.*;

class BatchPlannerTest {
  RepositorySnapshot snapshot(List<SourceFile> files) {
    return new RepositorySnapshot("key", files, List.of(), true);
  }

  @Test
  void preservesEveryCharacterAcrossBatches() {
    String source = "line A\nline B\n".repeat(1000);
    var plan =
        new BatchPlanner().partition(snapshot(List.of(new SourceFile("A.java", source))), 2000);
    assertTrue(plan.batches().size() > 1);
    String rebuilt =
        plan.batches().stream()
            .flatMap(b -> b.excerpts().stream())
            .map(SourceExcerpt::content)
            .collect(java.util.stream.Collectors.joining());
    assertEquals(source, rebuilt);
    assertTrue(plan.batches().stream().allMatch(b -> b.characters() <= 2000));
    assertEquals(source.length(), plan.submittedCharacters());
  }

  @Test
  void lineRangesRetainOriginalCoordinates() {
    String source = "one\ntwo\nthree\nfour\n";
    var p = new BatchPlanner().partition(snapshot(List.of(new SourceFile("A.java", source))), 8);
    assertEquals(1, p.batches().getFirst().excerpts().getFirst().startLine());
    assertTrue(
        p.batches().stream().flatMap(b -> b.excerpts().stream()).anyMatch(e -> e.startLine() > 1));
    for (var b : p.batches()) for (var e : b.excerpts()) assertTrue(e.endLine() >= e.startLine());
  }

  @Test
  void deterministicOrderAndEmptyFilesArePreserved() {
    var p =
        new BatchPlanner()
            .partition(
                snapshot(List.of(new SourceFile("Z.java", "z"), new SourceFile("A.java", ""))),
                2000);
    assertEquals(
        List.of("A.java", "Z.java"),
        p.batches().getFirst().excerpts().stream().map(SourceExcerpt::path).toList());
  }

  @Test
  void largeSqlIsExplicitlyReducedWithoutMutatingSource() {
    String sql = "INSERT INTO sample VALUES (1);\n".repeat(12000);
    var original = snapshot(List.of(new SourceFile("seed.sql", sql)));
    var p = new BatchPlanner().partition(original, 64000);
    assertEquals(sql, original.files().getFirst().content());
    assertEquals(sql.length(), p.originalCharacters());
    assertTrue(p.submittedCharacters() < 10000);
    assertEquals(1, p.reductions().size());
    assertTrue(p.reductions().getFirst().contains("não foram revisados integralmente"));
  }

  @Test
  void doesNotReduceLargeSourceCode() {
    String source = "class A {}\n".repeat(30000);
    var p =
        new BatchPlanner().partition(snapshot(List.of(new SourceFile("A.java", source))), 64000);
    assertTrue(p.reductions().isEmpty());
    assertEquals(p.originalCharacters(), p.submittedCharacters());
  }

  @Test
  void supplementaryUnicodeIsNotSplitIntoInvalidStrings() {
    String source = "a😀b😀c😀";
    var p = new BatchPlanner().partition(snapshot(List.of(new SourceFile("A.java", source))), 3);
    for (var b : p.batches())
      for (var e : b.excerpts()) {
        String text = e.content();
        assertFalse(Character.isLowSurrogate(text.charAt(0)));
        assertFalse(Character.isHighSurrogate(text.charAt(text.length() - 1)));
      }
    assertEquals(
        source,
        p.batches().stream()
            .flatMap(b -> b.excerpts().stream())
            .map(SourceExcerpt::content)
            .collect(java.util.stream.Collectors.joining()));
  }

  @Test
  void keepsFilesWholeWheneverEachFitsInOneBatch() {
    var snapshot =
        snapshot(
            List.of(
                new SourceFile("A.java", "a".repeat(1200)),
                new SourceFile("B.java", "b".repeat(1200))));
    var p = new BatchPlanner().partition(snapshot, 2000);
    assertEquals(2, p.batches().size());
    assertTrue(
        p.batches().stream()
            .flatMap(b -> b.excerpts().stream())
            .allMatch(SourceExcerpt::completeFile));
    assertEquals(1, p.batches().get(1).excerpts().getFirst().startLine());
  }

  @Test
  void numberedContentUsesOriginalLineCoordinates() {
    var excerpt = new SourceExcerpt("A.java", 20, 21, "first\nsecond");
    assertEquals("20 | first\n21 | second\n", excerpt.numberedContent());
  }
}
