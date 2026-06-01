package com.equily.identity.domain;

import java.time.Instant;
import java.util.Objects;

public class User {
  private final UserId id;
  private final String email;
  private final String passwordHash;
  private final String displayName;
  private final boolean emailVerified;
  private final Instant createdAt;

  private User(
      UserId id,
      String email,
      String passwordHash,
      String displayName,
      boolean emailVerified,
      Instant createdAt) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.displayName = displayName;
    this.emailVerified = emailVerified;
    this.createdAt = createdAt;
  }

  public static User register(String email, String passwordHash, String displayName) {
    Objects.requireNonNull(email, "email must not be null");
    Objects.requireNonNull(passwordHash, "passwordHash must not be null");
    Objects.requireNonNull(displayName, "displayName must not be null");
    if (email.isBlank()) throw new IllegalArgumentException("email must not be blank");
    return new User(
        UserId.generate(),
        email.toLowerCase().trim(),
        passwordHash,
        displayName,
        false,
        Instant.now());
  }

  public static User reconstruct(
      UserId id,
      String email,
      String passwordHash,
      String displayName,
      boolean emailVerified,
      Instant createdAt) {
    return new User(id, email, passwordHash, displayName, emailVerified, createdAt);
  }

  public UserId id() {
    return id;
  }

  public String email() {
    return email;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public String displayName() {
    return displayName;
  }

  public boolean emailVerified() {
    return emailVerified;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public User withEmailVerified() {
    return new User(id, email, passwordHash, displayName, true, createdAt);
  }

  public User withNewPassword(String newPasswordHash) {
    return new User(id, email, newPasswordHash, displayName, emailVerified, createdAt);
  }
}
