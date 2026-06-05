package com.equily.portfolio.domain;

import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable value object carrying the fields that can be updated on an existing transaction. Type
 * and ticker cannot be changed.
 */
public record UpdatedTransactionValues(
    BigDecimal quantity,
    Money pricePerUnit,
    Money totalAmount,
    LocalDate date,
    BigDecimal fees,
    String description) {

  public UpdatedTransactionValues {
    Objects.requireNonNull(totalAmount, "totalAmount must not be null");
    Objects.requireNonNull(date, "date must not be null");
    Objects.requireNonNull(fees, "fees must not be null");
  }
}
