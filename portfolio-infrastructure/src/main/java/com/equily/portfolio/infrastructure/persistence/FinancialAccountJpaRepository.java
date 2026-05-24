package com.equily.portfolio.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data JPA requires a public interface to create a JDK dynamic proxy.
// The adapter is the only caller; no other code should use this directly.
public interface FinancialAccountJpaRepository
    extends JpaRepository<FinancialAccountJpaEntity, UUID> {}
