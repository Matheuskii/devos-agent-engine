package io.devos.infrastructure;

import io.devos.application.*;
import io.devos.domain.*;
import java.time.Duration;

public interface ModelClient {
  String generate(String system, String input, Duration timeout);
}
