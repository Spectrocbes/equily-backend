package com.equily.portfolio.web;

import java.math.BigDecimal;

record TopPerformerResponse(
    String ticker,
    String accountName,
    BigDecimal currentValue,
    BigDecimal totalInvested,
    BigDecimal pnl,
    BigDecimal pnlPercent,
    BigDecimal dayChangePercent) {}
