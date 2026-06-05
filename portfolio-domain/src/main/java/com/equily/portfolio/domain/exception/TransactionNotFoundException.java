package com.equily.portfolio.domain.exception;

import com.equily.portfolio.domain.TransactionId;

public class TransactionNotFoundException extends RuntimeException {
  public TransactionNotFoundException(TransactionId id) {
    super("Transaction not found: " + id.value());
  }
}
