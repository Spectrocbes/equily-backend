package com.equily.portfolio.domain.exception;

import com.equily.portfolio.domain.FinancialAccountId;

public class AccountClosedException extends RuntimeException {
  public AccountClosedException(FinancialAccountId id) {
    super("Account " + id.value() + " is closed and cannot accept new transactions");
  }
}
