package com.equily.portfolio.web;

import java.math.BigDecimal;

public record EnrichedHoldingResponse(
    String ticker,
    BigDecimal quantity,
    BigDecimal averageCostPrice,
    BigDecimal totalInvested,
    BigDecimal totalFeesPaid,
    BigDecimal currentPrice,
    String currency,
    BigDecimal marketValue,
    BigDecimal unrealizedPnl,
    BigDecimal unrealizedPnlPct,
    boolean priceAvailable) {}
