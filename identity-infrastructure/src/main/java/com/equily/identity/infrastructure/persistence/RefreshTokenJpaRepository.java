package com.equily.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {
  Optional<RefreshTokenJpaEntity> findByTokenHashAndRevokedAtIsNull(String tokenHash);

  @Modifying
  @Query(
      "UPDATE RefreshTokenJpaEntity r SET r.revokedAt = :now"
          + " WHERE r.userId = :userId AND r.revokedAt IS NULL")
  void revokeAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
