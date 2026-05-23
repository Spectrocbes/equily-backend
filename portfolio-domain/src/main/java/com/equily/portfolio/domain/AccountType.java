package com.equily.portfolio.domain;

public enum AccountType {
  // Stock wrappers
  PEA,
  PEA_PME,
  COMPTE_TITRES,
  // Retirement
  PER,
  // Insurance
  ASSURANCE_VIE,
  // Liquid savings — covers Livret A, LDDS, LEP, Livret Jeune, CAT etc. (name field is free text)
  SAVINGS_ACCOUNT,
  // Current account
  CASH_ACCOUNT,
  // Alternative assets
  CRYPTO_WALLET,
  REAL_ESTATE
}
