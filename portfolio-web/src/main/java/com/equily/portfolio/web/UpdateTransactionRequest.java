package com.equily.portfolio.web;

import com.equily.portfolio.web.validation.ValidTransactionDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateTransactionRequest(
    @NotNull BigDecimal totalAmount,
    @NotNull @ValidTransactionDate LocalDate date,
    @DecimalMin(value = "0.0", message = "Brokerage fees cannot be negative") @NotNull
        BigDecimal fees,
    BigDecimal quantity,
    BigDecimal pricePerUnit,
    String description) {}
