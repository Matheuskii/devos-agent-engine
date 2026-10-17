package io.devos.domain;

public final class Checks {
  private Checks() {}

  public static String text(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " deve ser preenchido");
    return value;
  }
}
