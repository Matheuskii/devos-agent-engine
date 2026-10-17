package io.devos.application;

import java.util.*;

public final class BatchPlanner {
  public BatchPlan partition(RepositorySnapshot snapshot, int limit) {
    if (limit < 2) throw new IllegalArgumentException("Tamanho de lote inválido");
    var reductions = new ArrayList<String>();
    var reduced = new ArrayList<SourceFile>();
    for (var file : snapshot.files()) {
      if (file.path().toLowerCase(Locale.ROOT).endsWith(".sql")
          && file.content().length() > 250000) {
        var lines = file.content().split("\\n", -1);
        var out = new StringBuilder();
        out.append(
            "[DADOS SQL EXTENSOS: representação estrutural; o arquivo original foi lido"
                + " integralmente.]\n");
        out.append("Caracteres originais: ")
            .append(file.content().length())
            .append("; linhas: ")
            .append(lines.length)
            .append(".\n");
        var commands = new TreeMap<String, Integer>();
        var pattern =
            java.util.regex.Pattern.compile(
                "(?im)^\\s*(INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|SET|SELECT|COMMIT|BEGIN)\\b");
        var matcher = pattern.matcher(file.content());
        while (matcher.find())
          commands.merge(matcher.group(1).toUpperCase(Locale.ROOT), 1, Integer::sum);
        out.append("Comandos iniciados por linha (contagem heurística): ")
            .append(commands)
            .append(".\n");
        out.append(
            "Esta representação não valida a semântica de cada linha de dados. Não use estes"
                + " números como auditoria SQL.\n");
        // Cabeçalho e final ficam contextualizados, sem atribuir linhas originais à representação.
        out.append("Início do arquivo (amostra de dados, não evidência para apontar defeitos):\n");
        for (int i = 0; i < Math.min(20, lines.length) && out.length() < 6000; i++)
          out.append(lines[i], 0, Math.min(lines[i].length(), 240)).append('\n');
        String summary = out.toString();
        reduced.add(new SourceFile(file.path(), summary));
        reductions.add(
            file.path()
                + ": "
                + file.content().length()
                + " caracteres lidos; representação estrutural de "
                + summary.length()
                + " caracteres enviada à IA. Dados individuais não foram revisados integralmente"
                + " pela IA.");
      } else reduced.add(file);
    }
    reduced.sort(Comparator.comparing(SourceFile::path));
    var groups = new ArrayList<List<SourceExcerpt>>();
    var current = new ArrayList<SourceExcerpt>();
    int used = 0;
    for (var file : reduced) {
      String content = file.content();
      int offset = 0, line = 1;
      if (!current.isEmpty() && content.length() <= limit && used + content.length() > limit) {
        groups.add(List.copyOf(current));
        current.clear();
        used = 0;
      }
      boolean originalComplete =
          !content.startsWith("[DADOS SQL EXTENSOS")
              && !snapshot.notices().contains("Arquivo truncado: " + file.path());
      if (content.isEmpty()) {
        current.add(new SourceExcerpt(file.path(), 1, 1, "", originalComplete));
        continue;
      }
      while (offset < content.length()) {
        if (used == limit) {
          groups.add(List.copyOf(current));
          current.clear();
          used = 0;
        }
        int take = Math.min(limit - used, content.length() - offset);
        if (offset + take < content.length() && take > 1000) {
          int newline = content.lastIndexOf('\n', offset + take - 1);
          if (newline >= offset + take / 2) take = newline - offset + 1;
        }
        if (offset + take < content.length()
            && Character.isHighSurrogate(content.charAt(offset + take - 1))
            && Character.isLowSurrogate(content.charAt(offset + take))) {
          take--;
          if (take == 0) {
            groups.add(List.copyOf(current));
            current.clear();
            used = 0;
            continue;
          }
        }
        String part = content.substring(offset, offset + take);
        int breaks = (int) part.chars().filter(c -> c == '\n').count();
        int endLine = line + breaks - (part.endsWith("\n") ? 1 : 0);
        current.add(
            new SourceExcerpt(
                file.path(),
                line,
                Math.max(line, endLine),
                part,
                originalComplete && offset == 0 && take == content.length()));
        used += take;
        offset += take;
        line += breaks;
      }
    }
    if (!current.isEmpty()) groups.add(List.copyOf(current));
    var batches = new ArrayList<AnalysisBatch>();
    for (int i = 0; i < groups.size(); i++)
      batches.add(new AnalysisBatch(i + 1, groups.size(), groups.get(i)));
    return new BatchPlan(
        batches,
        reduced,
        reductions,
        snapshot.files().stream().mapToLong(f -> f.content().length()).sum(),
        reduced.stream().mapToLong(f -> f.content().length()).sum());
  }
}
