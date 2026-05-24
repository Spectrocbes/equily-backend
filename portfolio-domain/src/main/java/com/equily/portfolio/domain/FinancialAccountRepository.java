package com.equily.portfolio.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port (output port in Hexagonal Architecture). Defines what the domain needs from persistence —
 * not how it is implemented. The implementation lives in portfolio-infrastructure.
 */
public interface FinancialAccountRepository {
  void save(FinancialAccount account);

  Optional<FinancialAccount> findById(FinancialAccountId id);

  List<FinancialAccount> findAll();
}
