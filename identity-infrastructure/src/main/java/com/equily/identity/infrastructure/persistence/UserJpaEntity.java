package com.equily.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "users", schema = "identity")
class UserJpaEntity implements Persistable<UUID> {

  @Id UUID id;

  @Column(nullable = false, unique = true)
  String email;

  @Column(name = "password_hash")
  String passwordHash;

  @Column(name = "display_name", nullable = false, length = 100)
  String displayName;

  @Column(name = "email_verified", nullable = false)
  boolean emailVerified;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  @Transient private boolean isNew = true;

  protected UserJpaEntity() {}

  UserJpaEntity(
      UUID id,
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

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    this.isNew = false;
  }

  void markAsExisting() {
    this.isNew = false;
  }

  String getEmail() {
    return email;
  }

  String getPasswordHash() {
    return passwordHash;
  }

  void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  String getDisplayName() {
    return displayName;
  }

  boolean isEmailVerified() {
    return emailVerified;
  }

  void setEmailVerified(boolean emailVerified) {
    this.emailVerified = emailVerified;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
