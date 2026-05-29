package com.equily.identity.domain;

import java.util.Objects;
import java.util.UUID;

public record HouseholdId(UUID value) {
  public HouseholdId {
    Objects.requireNonNull(value, "HouseholdId must not be null");
  }

  public static HouseholdId generate() {
    return new HouseholdId(UUID.randomUUID());
  }

  public static HouseholdId of(String value) {
    return new HouseholdId(UUID.fromString(value));
  }
}
