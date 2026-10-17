package io.devos.infrastructure;

import io.devos.application.*;
import io.devos.domain.*;

public final class NoMemory implements MemoryPort {
  public String recall(String key, String objective, java.time.Duration timeout) {
    return "";
  }

  public void remember(String key, String objective, String summary, java.time.Duration timeout) {}
}
