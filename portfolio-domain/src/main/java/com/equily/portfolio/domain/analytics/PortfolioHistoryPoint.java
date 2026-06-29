package com.equily.portfolio.domain.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioHistoryPoint(
    LocalDate date, BigDecimal totalValue, BigDecimal invested, BigDecimal pnl) {}
