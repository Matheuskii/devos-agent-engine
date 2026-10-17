package io.devos.application;

import io.devos.domain.*;

public interface ToolPort {
  ToolId id();

  ToolResult execute(RepositorySnapshot snapshot);
}
