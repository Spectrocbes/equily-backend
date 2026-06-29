package com.equily.portfolio.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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
    LocalDate openedAt,
    BigDecimal portfolioValue,
    String status,
    LocalDate closedAt,
    UUID linkedCheckingAccountId,
    BigDecimal ownDeposits,
    String depositNote) {}
