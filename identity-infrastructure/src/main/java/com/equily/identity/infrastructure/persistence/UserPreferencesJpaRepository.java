package com.equily.identity.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserPreferencesJpaRepository extends JpaRepository<UserPreferencesJpaEntity, UUID> {}
