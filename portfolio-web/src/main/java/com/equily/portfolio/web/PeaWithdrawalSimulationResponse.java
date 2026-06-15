package com.equily.portfolio.web;

import java.math.BigDecimal;

public record PeaWithdrawalSimulationResponse(
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
