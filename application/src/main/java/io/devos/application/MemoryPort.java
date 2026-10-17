package io.devos.application;

import io.devos.domain.*;
import java.time.Duration;

public interface MemoryPort {
  String recall(String repositoryKey, String objective, Duration timeout);

  void remember(String repositoryKey, String objective, String summary, Duration timeout);
}
