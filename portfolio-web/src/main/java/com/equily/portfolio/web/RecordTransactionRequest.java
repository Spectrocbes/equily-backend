package com.equily.portfolio.web;

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
    @NotNull LocalDate date,
    String description) {}
