package com.equily.portfolio.domain;

import java.math.BigDecimal;

public record PeaWithdrawalSimulation(
    BigDecimal liquidationValue,
    BigDecimal totalDeposits,
    BigDecimal netGain,
    BigDecimal gainRatio,
    boolean atLoss,
    boolean peaOlderThan5Years,
    BigDecimal withdrawalAmount,
    BigDecimal taxableGain,
    BigDecimal irTax,
    BigDecimal psTax,
    BigDecimal totalTax,
    BigDecimal netAmount) {}
