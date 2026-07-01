package com.equily.portfolio.domain.analytics;

import java.math.BigDecimal;

public record TopPerformer(
    String ticker,
    String accountName,
    BigDecimal currentValue,
    BigDecimal totalInvested,
    BigDecimal pnl,
    BigDecimal pnlPercent,
    BigDecimal dayChangePercent) {}
