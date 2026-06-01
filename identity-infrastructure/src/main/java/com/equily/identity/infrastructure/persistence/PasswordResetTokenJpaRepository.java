package com.equily.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenJpaEntity, UUID> {

  Optional<PasswordResetTokenJpaEntity> findByTokenHashAndUsedAtIsNull(String tokenHash);

  @Modifying
  @Query("DELETE FROM PasswordResetTokenJpaEntity t WHERE t.userId = :userId")
  void deleteAllByUserId(@Param("userId") UUID userId);
}
