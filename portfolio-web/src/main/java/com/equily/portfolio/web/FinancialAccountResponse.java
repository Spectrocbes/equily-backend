package com.equily.portfolio.web;

import java.math.BigDecimal;

public record FinancialAccountResponse(
    String id,
    String name,
    String accountType,
    BigDecimal balance,
    String currency,
    int transactionCount,
    String broker) {}
