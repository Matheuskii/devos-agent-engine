package io.devos.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.devos.domain.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;

class HttpAdaptersTest {
  HttpServer server;
  URI base;
  final com.fasterxml.jackson.databind.ObjectMapper mapper =
      new com.fasterxml.jackson.databind.ObjectMapper();
  final List<com.fasterxml.jackson.databind.JsonNode> requests = new ArrayList<>();

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  void respond(com.sun.net.httpserver.HttpExchange x, int status, String json)
      throws java.io.IOException {
    byte[] data = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    x.getResponseHeaders().add("Content-Type", "application/json");
    x.sendResponseHeaders(status, data.length);
    try (var out = x.getResponseBody()) {
      out.write(data);
    }
  }

  @Test
  void groqSendsAuthAndExtractsCompletedContent() throws Exception {
    server.createContext(
        "/chat",
        x -> {
          assertEquals("Bearer test-key", x.getRequestHeaders().getFirst("Authorization"));
          requests.add(mapper.readTree(x.getRequestBody()));
          respond(
              x,
              200,
              "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"summary\\\":\\\"ok\\\"}\"}}]}");
        });
    server.start();
    var model =
        new ProviderModelClient(
            ProviderModelClient.Provider.GROQ,
            "test-key",
            "test-model",
            base.resolve("/chat"),
            new JsonHttp());
    assertEquals("{\"summary\":\"ok\"}", model.generate("system", "data", Duration.ofSeconds(2)));
    assertEquals("json_object", requests.getFirst().path("response_format").path("type").asText());
    assertEquals("system", requests.getFirst().path("messages").path(0).path("role").asText());
  }

  @Test
  void geminiSendsKeyInHeaderAndUsesGenerateContent() throws Exception {
    server.createContext(
        "/models/test:generateContent",
        x -> {
          assertEquals("test-key", x.getRequestHeaders().getFirst("x-goog-api-key"));
          assertNull(x.getRequestURI().getQuery());
          requests.add(mapper.readTree(x.getRequestBody()));
          respond(
              x,
              200,
              "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{\\\"summary\\\":\\\"ok\\\"}\"}]}}]}");
        });
    server.start();
    var model =
        new ProviderModelClient(
            ProviderModelClient.Provider.GEMINI, "test-key", "test", base, new JsonHttp());
    assertEquals("{\"summary\":\"ok\"}", model.generate("system", "data", Duration.ofSeconds(2)));
    assertEquals(
        "application/json",
        requests.getFirst().path("generationConfig").path("responseMimeType").asText());
  }

  @Test
  void rateLimitDoesNotLeakResponseOrCredential() throws Exception {
    server.createContext("/chat", x -> respond(x, 429, "secret-provider-body"));
    server.start();
    var model =
        new ProviderModelClient(
            ProviderModelClient.Provider.GROQ,
            "secret-key",
            "test",
            base.resolve("/chat"),
            new JsonHttp());
    var ex =
        assertThrows(
            IllegalStateException.class, () -> model.generate("s", "i", Duration.ofSeconds(2)));
    assertTrue(ex.getMessage().contains("429"));
    assertFalse(ex.getMessage().contains("secret"));
  }

  @Test
  void truncatedModelReplyIsRejected() throws Exception {
    server.createContext(
        "/chat",
        x ->
            respond(
                x,
                200,
                "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}"));
    server.start();
    var model =
        new ProviderModelClient(
            ProviderModelClient.Provider.GROQ,
            "key",
            "test",
            base.resolve("/chat"),
            new JsonHttp());
    assertThrows(
        IllegalStateException.class, () -> model.generate("s", "i", Duration.ofSeconds(2)));
  }

  @Test
  void qdrantCreatesCollectionScopesQueryAndUpsertsNormalizedVector() throws Exception {
    server.createContext(
        "/",
        x -> {
          String path = x.getRequestURI().getPath();
          String method = x.getRequestMethod();
          if (path.endsWith(":embedContent")) {
            requests.add(mapper.readTree(x.getRequestBody()));
            respond(x, 200, "{\"embedding\":{\"values\":[3,4]}}");
          } else if (path.equals("/collections")) {
            respond(x, 200, "{\"result\":{\"collections\":[]}}");
          } else if (path.endsWith("/points/query")) {
            var data = mapper.readTree(x.getRequestBody());
            requests.add(data);
            respond(
                x,
                200,
                "{\"result\":{\"points\":[{\"payload\":{\"repositoryKey\":\"repo-key\",\"summary\":\"Prior"
                    + " approved summary\"}}]}}");
          } else {
            assertEquals("PUT", method);
            requests.add(mapper.readTree(x.getRequestBody()));
            respond(x, 200, "{\"result\":true}");
          }
        });
    server.start();
    var http = new JsonHttp();
    var memory =
        new QdrantMemory(http, new GeminiEmbedding(http, "key", "embedding-test", 2, base), base);
    assertEquals(
        "Prior approved summary", memory.recall("repo-key", "objective", Duration.ofSeconds(3)));
    memory.remember("repo-key", "objective", "summary", Duration.ofSeconds(3));
    var query = requests.stream().filter(n -> n.has("query")).findFirst().orElseThrow();
    assertEquals(
        "repo-key", query.path("filter").path("must").path(0).path("match").path("value").asText());
    assertEquals(0.6, query.path("query").path(0).asDouble(), 0.0001);
    var upsert = requests.stream().filter(n -> n.has("points")).findFirst().orElseThrow();
    assertEquals(
        "repo-key", upsert.path("points").path(0).path("payload").path("repositoryKey").asText());
    assertEquals(0.8, upsert.path("points").path(0).path("vector").path(1).asDouble(), 0.0001);
  }

  @Test
  void qdrantRejectsNonLocalAddress() {
    var http = new JsonHttp();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new QdrantMemory(
                http,
                new GeminiEmbedding(http, "key", "test", 2, base),
                URI.create("http://remote.example:6333")));
  }

  @Test
  void embeddingDimensionMismatchFails() throws Exception {
    server.createContext(
        "/models/test:embedContent", x -> respond(x, 200, "{\"embedding\":{\"values\":[1]}}"));
    server.start();
    assertThrows(
        IllegalStateException.class,
        () ->
            new GeminiEmbedding(new JsonHttp(), "key", "test", 2, base)
                .embed("text", Duration.ofSeconds(2)));
  }

  @Test
  void bodySubscriberCancelsBeforeAccumulatingExcessData() {
    var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
    var subscriber = new LimitedBodySubscriber(16);
    subscriber.onSubscribe(
        new java.util.concurrent.Flow.Subscription() {
          public void request(long n) {}

          public void cancel() {
            cancelled.set(true);
          }
        });
    subscriber.onNext(List.of(java.nio.ByteBuffer.wrap(new byte[17])));
    assertTrue(cancelled.get());
    assertThrows(
        java.util.concurrent.CompletionException.class,
        () -> subscriber.getBody().toCompletableFuture().join());
  }

  @Test
  void timeoutCoversResponseBodyAfterHeaders() {
    server.createContext(
        "/slow",
        x -> {
          x.sendResponseHeaders(200, 2);
          x.getResponseBody().flush();
          java.util.concurrent.locks.LockSupport.parkNanos(Duration.ofMillis(300).toNanos());
          try (var output = x.getResponseBody()) {
            output.write("{}".getBytes());
          } catch (java.io.IOException ignored) {
            /* cliente pode ter cancelado */
          }
        });
    server.start();
    long start = System.nanoTime();
    assertThrows(
        IllegalStateException.class,
        () ->
            new JsonHttp()
                .send("GET", base.resolve("/slow"), Map.of(), null, Duration.ofMillis(80)));
    assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 1000);
  }
}
