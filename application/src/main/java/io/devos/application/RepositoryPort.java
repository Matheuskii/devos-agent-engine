package io.devos.application;

import io.devos.domain.*;
import java.nio.file.Path;

public interface RepositoryPort {
  RepositorySnapshot read(Path root);
}
