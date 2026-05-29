package com.equily.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {
  Optional<UserJpaEntity> findByEmail(String email);

  boolean existsByEmail(String email);
}
