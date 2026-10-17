package io.devos.infrastructure;

import io.devos.application.*;
import io.devos.domain.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

public final class LocalRepository implements RepositoryPort {
  private static final Set<String> SKIP_DIRS =
      Set.of(
          ".git",
          ".idea",
          ".vscode",
          "node_modules",
          "target",
          "build",
          "dist",
          "vendor",
          ".venv",
          "venv",
          ".tools",
          ".devos",
          "devos-output",
          "coverage",
          ".dart_tool");
  private static final Set<String> EXT =
      Set.of(
          "java",
          "kt",
          "py",
          "js",
          "jsx",
          "ts",
          "tsx",
          "dart",
          "cs",
          "go",
          "rs",
          "md",
          "txt",
          "xml",
          "json",
          "yaml",
          "yml",
          "toml",
          "gradle",
          "properties",
          "sql",
          "sh");
  private final int maxFiles, maxTotalBytes, maxFileBytes;

  public LocalRepository() {
    this(200, 120_000, 12_000);
  }

  public LocalRepository(int maxFiles, int maxTotalBytes, int maxFileBytes) {
    if (maxFiles < 1 || maxTotalBytes < 1 || maxFileBytes < 1)
      throw new IllegalArgumentException("Limites de leitura inválidos");
    this.maxFiles = maxFiles;
    this.maxTotalBytes = maxTotalBytes;
    this.maxFileBytes = maxFileBytes;
  }

  @Override
  public RepositorySnapshot read(Path input) {
    try {
      Path root = input.toRealPath();
      if (!Files.isDirectory(root))
        throw new IllegalArgumentException("Informe um diretório de repositório");
      var files = new ArrayList<SourceFile>();
      var notices = new ArrayList<String>();
      boolean[] complete = {true};
      int[] bytes = {0}, visited = {0};
      var matchers = new ArrayList<PathMatcher>();
      Path ignores = root.resolve(".devosignore");
      if (Files.isRegularFile(ignores, LinkOption.NOFOLLOW_LINKS) && Files.size(ignores) < 16000) {
        for (String pattern : Files.readAllLines(ignores)) {
          String p = pattern.strip();
          if (!p.isBlank() && !p.startsWith("#"))
            matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + p));
        }
      }
      Files.walkFileTree(
          root,
          EnumSet.noneOf(FileVisitOption.class),
          20,
          new SimpleFileVisitor<>() {
            private boolean ignored(Path path) {
              var relative = root.relativize(path);
              return matchers.stream().anyMatch(m -> m.matches(relative));
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              if (++visited[0] > 10000) {
                complete[0] = false;
                notices.add("Limite de entradas atingido; amostra parcial.");
                return FileVisitResult.TERMINATE;
              }
              if (!dir.equals(root)
                  && (SKIP_DIRS.contains(dir.getFileName().toString()) || ignored(dir)))
                return FileVisitResult.SKIP_SUBTREE;
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs)
                throws IOException {
              if (++visited[0] > 10000 || files.size() >= maxFiles || bytes[0] >= maxTotalBytes) {
                complete[0] = false;
                notices.add("Limite de arquivos/conteúdo atingido; amostra parcial.");
                return FileVisitResult.TERMINATE;
              }
              if (attrs.isDirectory()) {
                complete[0] = false;
                notices.add("Profundidade máxima atingida.");
                return FileVisitResult.CONTINUE;
              }
              if (!attrs.isRegularFile() || attrs.isSymbolicLink() || ignored(path))
                return FileVisitResult.CONTINUE;
              String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
              if (name.startsWith(".")
                  || name.contains("secret")
                  || name.contains("credential")
                  || name.contains("lock")
                  || name.endsWith(".min.js")) return FileVisitResult.CONTINUE;
              int dot = name.lastIndexOf('.');
              String ext = dot < 0 ? "" : name.substring(dot + 1);
              if (!EXT.contains(ext) && !Set.of("dockerfile", "makefile").contains(name))
                return FileVisitResult.CONTINUE;
              int limit = Math.min(maxFileBytes, maxTotalBytes - bytes[0]);
              byte[] data;
              try (var in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                data = in.readNBytes(limit + 1);
              }
              if (data.length > limit) {
                data = Arrays.copyOf(data, limit);
                complete[0] = false;
                notices.add("Arquivo truncado: " + root.relativize(path));
              }
              bytes[0] += data.length;
              String content = new String(data, StandardCharsets.UTF_8);
              if (content.indexOf('\0') >= 0) return FileVisitResult.CONTINUE;
              if (content.contains("PRIVATE KEY")) {
                notices.add("Arquivo com chave privada omitido.");
                return FileVisitResult.CONTINUE;
              }
              content = redact(content);
              files.add(
                  new SourceFile(root.relativize(path).toString().replace('\\', '/'), content));
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException ex) {
              complete[0] = false;
              notices.add("Arquivo não pôde ser lido: " + root.relativize(file));
              return FileVisitResult.CONTINUE;
            }
          });
      files.sort(Comparator.comparing(SourceFile::path));
      if (files.isEmpty())
        throw new IllegalArgumentException("Nenhum arquivo de texto elegível no repositório");
      String key =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(root.toString().getBytes(StandardCharsets.UTF_8)));
      return new RepositorySnapshot(key, files, notices, complete[0]);
    } catch (IOException ex) {
      throw new IllegalArgumentException("Não foi possível ler o diretório do repositório");
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 indisponível");
    }
  }

  public static String redact(String content) {
    return content
        .replaceAll(
            "(?im)^.*(?:password|passwd|api[_-]?key|access[_-]?token|authorization|client[_-]?secret)[\"']?\\s*[:=].*$",
            "[REDACTED]")
        .replaceAll(
            "(?:AIza[0-9A-Za-z_-]{25,}|gsk_[0-9A-Za-z]{20,}|gh[pousr]_[0-9A-Za-z]{20,})",
            "[REDACTED]");
  }
}
