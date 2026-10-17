package io.devos.infrastructure;

import com.fasterxml.jackson.databind.*;
import io.devos.application.*;
import io.devos.domain.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public final class JsonHttp {
  private final HttpClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public JsonHttp() {
    client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public JsonNode send(
      String method, URI uri, Map<String, String> headers, Object body, Duration timeout) {
    try {
      var request =
          HttpRequest.newBuilder(uri)
              .timeout(
                  timeout.compareTo(Duration.ofSeconds(40)) < 0 ? timeout : Duration.ofSeconds(40))
              .header("Content-Type", "application/json");
      headers.forEach(request::header);
      request.method(
          method,
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
      var pending = client.sendAsync(request.build(), info -> new LimitedBodySubscriber(2_000_000));
      HttpResponse<byte[]> response;
      try {
        response =
            pending.get(
                Math.max(1, Math.min(timeout.toMillis(), 40_000)),
                java.util.concurrent.TimeUnit.MILLISECONDS);
      } catch (java.util.concurrent.TimeoutException ex) {
        pending.cancel(true);
        throw new IllegalStateException("Timeout na resposta completa do serviço externo");
      } catch (InterruptedException ex) {
        pending.cancel(true);
        throw ex;
      }
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new IllegalStateException(
            "Serviço externo retornou HTTP "
                + response.statusCode()
                + "; confira credenciais, modelo e cotas.");
      if (response.body().length > 2_000_000)
        throw new IllegalStateException("Resposta externa excede limite de tamanho");
      return mapper.readTree(response.body());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Chamada externa interrompida");
    } catch (java.util.concurrent.ExecutionException ex) {
      throw new IllegalStateException(
          "Falha de comunicação ou limite de resposta no serviço externo");
    } catch (IOException ex) {
      throw new IllegalStateException("Falha de comunicação ou JSON inválido no serviço externo");
    }
  }

  public ObjectMapper mapper() {
    return mapper;
  }
}
