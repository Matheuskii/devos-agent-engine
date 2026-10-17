package io.devos.application;

@FunctionalInterface
public interface ProgressListener {
  void update(String message);

  static ProgressListener silent() {
    return message -> {};
  }
}
