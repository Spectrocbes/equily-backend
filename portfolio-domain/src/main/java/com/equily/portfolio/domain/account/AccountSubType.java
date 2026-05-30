package com.equily.portfolio.domain.account;

/**
 * Sub-type of a financial account, used to apply specific regulatory rules. Each AccountType maps
 * to one or more AccountSubTypes.
 *
 * <p>Regulatory limits are defined in DepositLimits — update that class when the government changes
 * the rules.
 */
public enum AccountSubType {
  // Savings — each has a distinct legal deposit cap
  LIVRET_A, // Art. L221-1 CMF — plafond 22 950 €
  LDDS, // Art. L221-27 CMF — plafond 12 000 €
  LEP, // Art. L221-14 CMF — plafond 10 000 €
  LDD, // Alias for LDDS (older name)
  LIVRET_JEUNE, // Art. L221-24 CMF — plafond 1 600 €

  // Investments
  PEA, // Art. L221-30 CMF — plafond versements 150 000 €
  PEA_PME, // Art. L221-32 CMF — plafond combiné PEA+PEA-PME 225 000 €
  COMPTE_TITRES,
  PER,
  ASSURANCE_VIE,

  // Other
  CRYPTO_WALLET,
  CASH_ACCOUNT,
  REAL_ESTATE,

  // Generic fallback — no regulatory limit
  OTHER
}
