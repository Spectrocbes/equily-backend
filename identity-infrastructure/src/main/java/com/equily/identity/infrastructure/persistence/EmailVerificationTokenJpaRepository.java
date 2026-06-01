package com.equily.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EmailVerificationTokenJpaRepository
    extends JpaRepository<EmailVerificationTokenJpaEntity, UUID> {

  Optional<EmailVerificationTokenJpaEntity> findByTokenHashAndUsedAtIsNull(String tokenHash);

  @Modifying
  @Query("DELETE FROM EmailVerificationTokenJpaEntity t WHERE t.userId = :userId")
  void deleteAllByUserId(@Param("userId") UUID userId);
}
