package com.equily.portfolio.domain.exception;

import java.math.BigDecimal;

public class InvalidHoldingException extends RuntimeException {

  public InvalidHoldingException(String message) {
    super(message);
  }

  public InvalidHoldingException(String ticker, BigDecimal attempted, BigDecimal held) {
    super(
        String.format(
            "Cannot sell %s %s — you only hold %s",
            formatQuantity(attempted), ticker, formatQuantity(held)));
  }

  private static String formatQuantity(BigDecimal qty) {
    return qty.stripTrailingZeros().toPlainString();
  }
}
