package com.equily.identity.domain;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {
  public UserId {
    Objects.requireNonNull(value, "UserId must not be null");
  }

  public static UserId generate() {
    return new UserId(UUID.randomUUID());
  }

  public static UserId of(String value) {
    return new UserId(UUID.fromString(value));
  }
}
