package io.devos.infrastructure;

import io.devos.application.*;
import io.devos.domain.*;
import java.util.*;

public final class InventoryTool implements ToolPort {
  public ToolId id() {
    return ToolId.INVENTORY;
  }

  public ToolResult execute(RepositorySnapshot snapshot) {
    var extensions = new TreeMap<String, Integer>();
    for (var file : snapshot.files()) {
      String path = file.path();
      int dot = path.lastIndexOf('.');
      String ext = dot < 0 ? "sem extensão" : path.substring(dot + 1);
      extensions.merge(ext, 1, Integer::sum);
    }
    return new ToolResult(
        id(),
        "Arquivos elegíveis na amostra: "
            + snapshot.files().size()
            + "; extensões: "
            + extensions
            + "; amostra completa nos limites configurados: "
            + snapshot.complete(),
        List.of());
  }
}
