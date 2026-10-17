package io.devos.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import io.devos.application.*;
import io.devos.domain.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;

public final class ProviderModelClient implements ModelClient {
  public enum Provider {
    GEMINI,
    GROQ
  }

  private final Provider provider;
  private final String apiKey, model;
  private final URI endpoint;
  private final JsonHttp http;

  public ProviderModelClient(
      Provider provider, String apiKey, String model, URI endpoint, JsonHttp http) {
    this.provider = Objects.requireNonNull(provider);
    this.apiKey = Checks.text(apiKey, "API key");
    this.model = Checks.text(model, "Modelo");
    if (!model.matches("[a-zA-Z0-9._/-]+"))
      throw new IllegalArgumentException("Identificador de modelo inválido");
    this.endpoint = Objects.requireNonNull(endpoint);
    this.http = http;
  }

  public String generate(String system, String input, Duration timeout) {
    JsonNode response;
    if (provider == Provider.GROQ) {
      response =
          http.send(
              "POST",
              endpoint,
              Map.of("Authorization", "Bearer " + apiKey),
              Map.of(
                  "model",
                  model,
                  "messages",
                  List.of(
                      Map.of("role", "system", "content", system),
                      Map.of("role", "user", "content", input)),
                  "temperature",
                  0.1,
                  "max_tokens",
                  2500,
                  "response_format",
                  Map.of("type", "json_object")),
              timeout);
      if (!response.path("choices").path(0).path("finish_reason").asText().equals("stop"))
        throw new IllegalStateException("Modelo não finalizou a resposta normalmente");
      return Checks.text(
          response.path("choices").path(0).path("message").path("content").asText(),
          "Resposta do modelo");
    }
    URI uri = URI.create(endpoint.toString() + "/models/" + model + ":generateContent");
    response =
        http.send(
            "POST",
            uri,
            Map.of("x-goog-api-key", apiKey),
            Map.of(
                "systemInstruction",
                Map.of("parts", List.of(Map.of("text", system))),
                "contents",
                List.of(Map.of("parts", List.of(Map.of("text", input)))),
                "generationConfig",
                Map.of(
                    "temperature",
                    0.1,
                    "maxOutputTokens",
                    2500,
                    "responseMimeType",
                    "application/json")),
            timeout);
    var candidate = response.path("candidates").path(0);
    if (!candidate.path("finishReason").asText().equals("STOP"))
      throw new IllegalStateException("Modelo bloqueou ou truncou a resposta");
    var text = new StringBuilder();
    for (var part : candidate.path("content").path("parts"))
      if (!part.path("thought").asBoolean(false)) text.append(part.path("text").asText());
    return Checks.text(text.toString(), "Resposta do modelo");
  }
}
