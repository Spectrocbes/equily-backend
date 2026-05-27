package com.equily.portfolio.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(
    String id,
    String type,
    String ticker,
    BigDecimal quantity,
    BigDecimal pricePerUnit,
    BigDecimal totalAmount,
    String currency,
    LocalDate date,
    BigDecimal fees,
    String description) {}
