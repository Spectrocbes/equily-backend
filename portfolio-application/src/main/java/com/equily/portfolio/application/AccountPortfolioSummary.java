package com.equily.portfolio.application;

import com.equily.portfolio.domain.FinancialAccountId;
import java.math.BigDecimal;

public record AccountPortfolioSummary(
    FinancialAccountId accountId,
    BigDecimal livePortfolioValue,
    BigDecimal costPortfolioValue,
    BigDecimal unrealizedPnl,
    BigDecimal unrealizedPnlPct,
    boolean priceAvailable) {}
