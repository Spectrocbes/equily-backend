package com.equily.portfolio.web;

import com.equily.portfolio.web.validation.ValidTransactionDate;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateTransactionRequest(
    @DecimalMin(
            value = "0",
            inclusive = false,
            message = "Transaction amount must be greater than zero")
        BigDecimal totalAmount,
    @NotNull @ValidTransactionDate LocalDate date,
    @DecimalMin(value = "0.0", message = "Brokerage fees cannot be negative") @NotNull
        BigDecimal fees,
    BigDecimal quantity,
    BigDecimal pricePerUnit,
    String description) {

  @AssertTrue(message = "Quantity must be greater than zero")
  public boolean isQuantityValid() {
    if (quantity == null) return true;
    return quantity.compareTo(BigDecimal.ZERO) > 0;
  }

  @AssertTrue(message = "Price per unit must be greater than zero")
  public boolean isPriceValid() {
    if (pricePerUnit == null) return true;
    return pricePerUnit.compareTo(BigDecimal.ZERO) > 0;
  }
}
