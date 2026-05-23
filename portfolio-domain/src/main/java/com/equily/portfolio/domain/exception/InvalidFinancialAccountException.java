package com.equily.portfolio.domain.exception;

public class InvalidFinancialAccountException extends RuntimeException {
  public InvalidFinancialAccountException(String message) {
    super(message);
  }
}
