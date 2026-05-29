package com.equily.portfolio.domain;

import com.equily.identity.domain.UserId;
import java.util.List;
import java.util.Optional;

/**
 * Port (output port in Hexagonal Architecture). Defines what the domain needs from persistence —
 * not how it is implemented. The implementation lives in portfolio-infrastructure.
 */
public interface FinancialAccountRepository {
  void save(FinancialAccount account);

  Optional<FinancialAccount> findById(FinancialAccountId id);

  /** Scoped to a specific user — primary query method for all user-facing reads. */
  List<FinancialAccount> findAllByOwnerId(UserId ownerId);

  /**
   * Returns all accounts across all users. For tests only — never called from application layer.
   */
  List<FinancialAccount> findAll();
}
