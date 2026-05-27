package com.equily.portfolio.web;

import java.math.BigDecimal;

public record HoldingResponse(
    String ticker,
    BigDecimal quantity,
    BigDecimal averageCostPrice,
    String currency,
    BigDecimal totalInvested,
    BigDecimal totalFeesPaid) {}
