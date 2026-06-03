package com.equily.portfolio.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialAccountResponse(
    String id,
    String name,
    String accountType,
    String subType,
    BigDecimal balance,
    String currency,
    int transactionCount,
    String broker,
    BigDecimal depositLimit,
    BigDecimal totalDeposits,
    BigDecimal remainingCapacity,
    LocalDate openedAt) {}
