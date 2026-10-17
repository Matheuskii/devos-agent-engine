package io.devos.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Cancela a transferência antes de acumular uma resposta excessiva na memória. */
final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
  private final int limit;
  private final ByteArrayOutputStream data = new ByteArrayOutputStream();
  private final CompletableFuture<byte[]> body = new CompletableFuture<>();
  private Flow.Subscription subscription;

  LimitedBodySubscriber(int limit) {
    this.limit = limit;
  }

  @Override
  public CompletionStage<byte[]> getBody() {
    return body;
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.subscription = subscription;
    subscription.request(1);
  }

  @Override
  public void onNext(List<ByteBuffer> buffers) {
    for (ByteBuffer buffer : buffers) {
      if (buffer.remaining() > limit - data.size()) {
        subscription.cancel();
        body.completeExceptionally(new IOException("Limite de resposta excedido"));
        return;
      }
      byte[] chunk = new byte[buffer.remaining()];
      buffer.get(chunk);
      data.writeBytes(chunk);
    }
    subscription.request(1);
  }

  @Override
  public void onError(Throwable error) {
    body.completeExceptionally(error);
  }

  @Override
  public void onComplete() {
    body.complete(data.toByteArray());
  }
}
