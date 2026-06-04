package com.equily.portfolio.web;

import java.math.BigDecimal;

public record PeaSummaryResponse(
    boolean hasPea,
    boolean hasPeaPme,
    BigDecimal peaDeposits,
    BigDecimal peaPmeDeposits,
    BigDecimal combinedDeposits,
    BigDecimal combinedLimit,
    BigDecimal combinedRemaining,
    BigDecimal peaLimit,
    BigDecimal peaRemaining,
    String peaAccountId,
    String peaPmeAccountId) {}
