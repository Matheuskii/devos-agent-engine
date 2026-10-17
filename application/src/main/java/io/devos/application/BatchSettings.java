package io.devos.application;

import java.time.Duration;

public record BatchSettings(int charactersPerBatch, int maxBatches, Duration delay) {
  public BatchSettings {
    if (charactersPerBatch < 2000
        || charactersPerBatch > 200000
        || maxBatches < 1
        || maxBatches > 500
        || delay == null
        || delay.isNegative()
        || delay.compareTo(Duration.ofSeconds(30)) > 0)
      throw new IllegalArgumentException("Configuração de lotes inválida");
  }

  public static BatchSettings defaults() {
    return new BatchSettings(64000, 64, Duration.ofSeconds(1));
  }
}
