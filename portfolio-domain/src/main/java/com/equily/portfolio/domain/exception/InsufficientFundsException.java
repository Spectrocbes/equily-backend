package com.equily.portfolio.domain.exception;

import com.equily.shared.Money;

public class InsufficientFundsException extends RuntimeException {

  public InsufficientFundsException(Money attempted, Money available) {
    super(
        String.format(
            "Insufficient funds — available: %s, required: %s",
            formatMoney(available), formatMoney(attempted)));
  }

  private static String formatMoney(Money m) {
    return String.format("%s %,.2f", m.currency().getSymbol(), m.amount());
  }
}
