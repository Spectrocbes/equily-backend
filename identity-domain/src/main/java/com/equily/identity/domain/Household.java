package com.equily.identity.domain;

import java.time.Instant;
import java.util.Objects;

public class Household {
  private final HouseholdId id;
  private final String name;
  private final UserId ownerId;
  private final Instant createdAt;

  private Household(HouseholdId id, String name, UserId ownerId, Instant createdAt) {
    this.id = id;
    this.name = name;
    this.ownerId = ownerId;
    this.createdAt = createdAt;
  }

  public static Household create(String name, UserId ownerId) {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
    return new Household(HouseholdId.generate(), name, ownerId, Instant.now());
  }

  public static Household reconstruct(
      HouseholdId id, String name, UserId ownerId, Instant createdAt) {
    return new Household(id, name, ownerId, createdAt);
  }

  public HouseholdId id() {
    return id;
  }

  public String name() {
    return name;
  }

  public UserId ownerId() {
    return ownerId;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
