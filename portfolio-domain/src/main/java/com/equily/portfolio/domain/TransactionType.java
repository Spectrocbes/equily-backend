package com.equily.portfolio.domain;

public enum TransactionType {
  DEPOSIT,
  WITHDRAWAL,
  PAYMENT, // payment to merchant/service — debit on CASH_ACCOUNT
  BUY,
  SELL,
  DIVIDEND,
  INTEREST,
  TRANSFER // movement between accounts (or external for crypto/cash)
}
