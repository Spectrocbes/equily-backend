package com.equily.portfolio.domain;

/** Direction of a TRANSFER transaction from the perspective of the recording account. */
public enum TransferDirection {
  INCOMING, // funds arriving — credit, adds to balance
  OUTGOING // funds leaving — debit, subtracts from balance
}
