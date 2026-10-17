package io.devos.infrastructure;

import io.devos.application.*;
import io.devos.domain.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;

public final class QdrantMemory implements MemoryPort {
  private final JsonHttp http;
  private final GeminiEmbedding embedding;
  private final URI endpoint;
  private final String collection;
  private boolean initialized;

  public QdrantMemory(JsonHttp http, GeminiEmbedding embedding, URI endpoint) {
    this.http = http;
    this.embedding = embedding;
    this.endpoint = endpoint;
    if (!Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(endpoint.getHost())
        || !endpoint.getScheme().equals("http")
        || endpoint.getUserInfo() != null
        || endpoint.getQuery() != null
        || endpoint.getFragment() != null
        || !Set.of("", "/").contains(endpoint.getPath()))
      throw new IllegalArgumentException(
          "Qdrant deve usar endereço HTTP local sem caminho, credenciais ou parâmetros");
    collection =
        "devos_" + embedding.model().replaceAll("[^a-zA-Z0-9]", "_") + "_" + embedding.dimensions();
  }

  private URI uri(String path) {
    return URI.create(endpoint.toString().replaceAll("/$", "") + path);
  }

  private Duration remaining(Duration total, long started) {
    var left = total.minusNanos(System.nanoTime() - started);
    if (left.isNegative() || left.isZero())
      throw new IllegalStateException("Tempo da operação de memória excedido");
    return left;
  }

  private void ensure(Duration total, long start) {
    if (initialized) return;
    var listed = http.send("GET", uri("/collections"), Map.of(), null, remaining(total, start));
    boolean exists = false;
    for (var item : listed.path("result").path("collections"))
      if (item.path("name").asText().equals(collection)) exists = true;
    if (!exists)
      http.send(
          "PUT",
          uri("/collections/" + collection),
          Map.of(),
          Map.of("vectors", Map.of("size", embedding.dimensions(), "distance", "Cosine")),
          remaining(total, start));
    else {
      var config =
          http.send(
                  "GET", uri("/collections/" + collection), Map.of(), null, remaining(total, start))
              .path("result")
              .path("config")
              .path("params")
              .path("vectors");
      if (config.path("size").asInt() != embedding.dimensions()
          || !config.path("distance").asText().equalsIgnoreCase("Cosine"))
        throw new IllegalStateException("Coleção Qdrant incompatível");
    }
    initialized = true;
  }

  public String recall(String repositoryKey, String objective, Duration timeout) {
    long start = System.nanoTime();
    ensure(timeout, start);
    var vector = embedding.embed(objective, remaining(timeout, start));
    var result =
        http.send(
            "POST",
            uri("/collections/" + collection + "/points/query"),
            Map.of(),
            Map.of(
                "query",
                vector,
                "limit",
                3,
                "with_payload",
                true,
                "filter",
                Map.of(
                    "must",
                    List.of(
                        Map.of("key", "repositoryKey", "match", Map.of("value", repositoryKey))))),
            remaining(timeout, start));
    var snippets = new ArrayList<String>();
    for (var point : result.path("result").path("points")) {
      var payload = point.path("payload");
      if (!payload.path("repositoryKey").asText().equals(repositoryKey))
        throw new IllegalStateException("Memória retornou repositório incompatível");
      var text = payload.path("summary").asText();
      snippets.add(text.substring(0, Math.min(text.length(), 4000)));
    }
    return String.join("\n", snippets);
  }

  public void remember(String repositoryKey, String objective, String summary, Duration timeout) {
    long start = System.nanoTime();
    ensure(timeout, start);
    var vector = embedding.embed(objective + "\n" + summary, remaining(timeout, start));
    String id =
        UUID.nameUUIDFromBytes(
                (repositoryKey + objective + summary)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .toString();
    http.send(
        "PUT",
        uri("/collections/" + collection + "/points?wait=true"),
        Map.of(),
        Map.of(
            "points",
            List.of(
                Map.of(
                    "id",
                    id,
                    "vector",
                    vector,
                    "payload",
                    Map.of(
                        "repositoryKey",
                        repositoryKey,
                        "summary",
                        summary,
                        "embeddingModel",
                        embedding.model())))),
        remaining(timeout, start));
  }
}
