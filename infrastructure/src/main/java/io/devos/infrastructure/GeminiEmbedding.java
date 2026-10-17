package io.devos.infrastructure;

import io.devos.application.*;
import io.devos.domain.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;

public final class GeminiEmbedding {
  private final JsonHttp http;
  private final String key, model;
  private final int dimensions;
  private final URI endpoint;

  public GeminiEmbedding(JsonHttp http, String key, String model, int dimensions, URI endpoint) {
    this.http = http;
    this.key = Checks.text(key, "Chave para embeddings");
    this.model = Checks.text(model, "Modelo de embedding");
    if (!model.matches("[a-zA-Z0-9._-]+") || dimensions < 1 || dimensions > 3072)
      throw new IllegalArgumentException("Configuração de embedding inválida");
    this.dimensions = dimensions;
    this.endpoint = endpoint;
  }

  public List<Double> embed(String text, Duration timeout) {
    var response =
        http.send(
            "POST",
            URI.create(endpoint + "/models/" + model + ":embedContent"),
            Map.of("x-goog-api-key", key),
            Map.of(
                "model",
                "models/" + model,
                "content",
                Map.of("parts", List.of(Map.of("text", text))),
                "outputDimensionality",
                dimensions),
            timeout);
    var values = response.path("embedding").path("values");
    if (!values.isArray() || values.size() != dimensions)
      throw new IllegalStateException("Dimensão do embedding incompatível");
    var vector = new ArrayList<Double>();
    double norm = 0;
    for (var value : values) {
      if (!value.isNumber() || !Double.isFinite(value.doubleValue()))
        throw new IllegalStateException("Embedding inválido");
      double v = value.doubleValue();
      vector.add(v);
      norm += v * v;
    }
    if (norm == 0 || !Double.isFinite(norm))
      throw new IllegalStateException("Embedding sem norma válida");
    double divisor = Math.sqrt(norm);
    return vector.stream().map(v -> v / divisor).toList();
  }

  public int dimensions() {
    return dimensions;
  }

  public String model() {
    return model;
  }
}
