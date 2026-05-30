package com.equily.portfolio.domain.exception;

import com.equily.portfolio.domain.account.AccountSubType;
import com.equily.shared.Money;
import java.math.BigDecimal;

/**
 * Thrown when a DEPOSIT transaction would cause the account's cumulative deposits to exceed the
 * regulatory limit.
 */
public class DepositLimitExceededException extends RuntimeException {

  private final AccountSubType subType;
  private final Money limit;
  private final Money currentTotal;
  private final Money attempted;
  private final Money remaining;

  public DepositLimitExceededException(
      AccountSubType subType, Money limit, Money currentTotal, Money attempted) {
    super(
        String.format(
            "Deposit limit exceeded for %s: limit=%s, current=%s, attempted=%s",
            subType, limit.amount(), currentTotal.amount(), attempted.amount()));
    this.subType = subType;
    this.limit = limit;
    this.currentTotal = currentTotal;
    this.attempted = attempted;
    this.remaining =
        new Money(
            limit.amount().subtract(currentTotal.amount()).max(BigDecimal.ZERO), limit.currency());
  }

  public AccountSubType subType() {
    return subType;
  }

  public Money limit() {
    return limit;
  }

  public Money currentTotal() {
    return currentTotal;
  }

  public Money attempted() {
    return attempted;
  }

  public Money remaining() {
    return remaining;
  }
}
