package com.equily.portfolio.web;

import com.equily.portfolio.web.validation.ValidTransactionAmount;
import com.equily.portfolio.web.validation.ValidTransactionDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;

@ValidTransactionAmount
public record RecordTransactionRequest(
    @NotBlank String type,
    String ticker,
    BigDecimal quantity,
    BigDecimal pricePerUnit,
    @NotNull BigDecimal totalAmount,
    @NotNull @ValidTransactionDate LocalDate date,
    @DecimalMin(value = "0.0", message = "Brokerage fees cannot be negative") BigDecimal fees,
    String description,
    @Pattern(regexp = "EUR|USD|GBP|CHF", message = "Currency must be one of: EUR, USD, GBP, CHF")
        String currency,
    String externalAddress) {

  public RecordTransactionRequest {
    if (currency == null) currency = "EUR";
  }
}
