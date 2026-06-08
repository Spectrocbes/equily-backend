package com.equily.portfolio.web;

import java.math.BigDecimal;

public record AccountPortfolioSummaryResponse(
    String accountId,
    BigDecimal livePortfolioValue,
    BigDecimal costPortfolioValue,
    BigDecimal unrealizedPnl,
    BigDecimal unrealizedPnlPct,
    boolean priceAvailable) {}
