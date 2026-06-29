package com.equily.portfolio.web;

import java.math.BigDecimal;

record PortfolioHistoryPointResponse(
    String date, BigDecimal value, BigDecimal invested, BigDecimal pnl) {}
