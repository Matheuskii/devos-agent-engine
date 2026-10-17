package io.devos.application;

import io.devos.domain.*;

public interface RunRepository {
  void save(RunRecord record);
}
