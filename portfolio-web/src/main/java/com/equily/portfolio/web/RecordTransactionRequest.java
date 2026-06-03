package com.equily.portfolio.web;

import com.equily.portfolio.web.validation.ValidTransactionDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordTransactionRequest(
    @NotBlank String type,
    String ticker,
    BigDecimal quantity,
    BigDecimal pricePerUnit,
    String priceCurrency,
    @NotNull BigDecimal totalAmount,
    @NotBlank String totalCurrency,
    @NotNull @ValidTransactionDate LocalDate date,
    @DecimalMin(value = "0.0", message = "Brokerage fees cannot be negative") BigDecimal fees,
    String description) {}
