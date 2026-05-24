package com.equily.portfolio.domain.exception;

import com.equily.portfolio.domain.FinancialAccountId;

public class AccountNotFoundException extends RuntimeException {
  public AccountNotFoundException(FinancialAccountId id) {
    super("FinancialAccount not found: " + id.value());
  }
}
