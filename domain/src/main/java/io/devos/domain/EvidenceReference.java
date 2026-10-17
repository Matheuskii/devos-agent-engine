package io.devos.domain;

public record EvidenceReference(String path, int startLine, int endLine, String quote) {
  public EvidenceReference {
    path = Checks.text(path, "Arquivo da evidência");
    quote = Checks.text(quote, "Trecho da evidência");
    if (startLine < 1 || endLine < startLine || endLine - startLine > 20 || quote.length() > 1000)
      throw new IllegalArgumentException("Intervalo ou trecho de evidência inválido");
  }
}
