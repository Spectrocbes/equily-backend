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
@Table(name = "refresh_tokens", schema = "identity")
class RefreshTokenJpaEntity implements Persistable<UUID> {

  @Id UUID id;

  @Column(name = "user_id", nullable = false)
  UUID userId;

  @Column(name = "token_hash", nullable = false, unique = true)
  String tokenHash;

  @Column(name = "expires_at", nullable = false)
  Instant expiresAt;

  @Column(name = "revoked_at")
  Instant revokedAt;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  @Transient private boolean isNew = true;

  protected RefreshTokenJpaEntity() {}

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

  void setId(UUID id) {
    this.id = id;
  }

  void setUserId(UUID userId) {
    this.userId = userId;
  }

  void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  void setRevokedAt(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }

  void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  UUID getUserId() {
    return userId;
  }

  Instant getExpiresAt() {
    return expiresAt;
  }
}
