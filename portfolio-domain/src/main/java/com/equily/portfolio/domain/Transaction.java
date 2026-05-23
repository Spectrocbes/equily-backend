package com.equily.portfolio.domain;

import com.equily.portfolio.domain.exception.InvalidTransactionException;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

public record Transaction(
    TransactionId id,
    TransactionType type,
    Ticker ticker,
    BigDecimal quantity,
    Money pricePerUnit,
    Money totalAmount,
    LocalDate date) {

  private static final Set<TransactionType> ASSET_TYPES =
      EnumSet.of(TransactionType.BUY, TransactionType.SELL);
  private static final Set<TransactionType> CASH_ONLY_TYPES =
      EnumSet.of(TransactionType.DEPOSIT, TransactionType.WITHDRAWAL);

  public Transaction {
    // validation is enforced in the static factory of(...); direct construction is intentionally
    // left open
    // for the record's canonical form used by computeFrom/infrastructure mappers
  }

  public static Transaction of(
      TransactionId id,
      TransactionType type,
      Ticker ticker,
      BigDecimal quantity,
      Money pricePerUnit,
      Money totalAmount,
      LocalDate date) {
    if (type == null) throw new InvalidTransactionException("type must not be null");
    if (date == null) throw new InvalidTransactionException("date must not be null");
    if (totalAmount == null) throw new InvalidTransactionException("totalAmount must not be null");

    if (ASSET_TYPES.contains(type)) {
      if (ticker == null)
        throw new InvalidTransactionException("ticker must not be null for " + type);
      if (quantity == null)
        throw new InvalidTransactionException("quantity must not be null for " + type);
      if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
        throw new InvalidTransactionException("quantity must be positive for " + type);
      }
      if (pricePerUnit == null)
        throw new InvalidTransactionException("pricePerUnit must not be null for " + type);
    }

    if (CASH_ONLY_TYPES.contains(type)) {
      if (quantity != null) {
        throw new InvalidTransactionException("quantity must be null for " + type);
      }
    }

    return new Transaction(id, type, ticker, quantity, pricePerUnit, totalAmount, date);
  }
}
