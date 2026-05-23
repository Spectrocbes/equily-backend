package com.equily.portfolio.domain;

import java.util.UUID;

public record FinancialAccountId(UUID value) {

  public FinancialAccountId {
    if (value == null)
      throw new IllegalArgumentException("FinancialAccountId value must not be null");
  }

  public static FinancialAccountId generate() {
    return new FinancialAccountId(UUID.randomUUID());
  }
}
