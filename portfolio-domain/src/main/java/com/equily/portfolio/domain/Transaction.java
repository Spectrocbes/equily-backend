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
    LocalDate date,
    BigDecimal fees,
    String description,
    String currency,
    BigDecimal amountEur,
    BigDecimal eurFxRate) {

  private static final Set<TransactionType> ASSET_TYPES =
      EnumSet.of(TransactionType.BUY, TransactionType.SELL);
  private static final Set<TransactionType> CASH_ONLY_TYPES =
      EnumSet.of(TransactionType.DEPOSIT, TransactionType.WITHDRAWAL);

  public Transaction {
    // validation is enforced in the static factory of(...); direct construction is intentionally
    // left open for the record's canonical form used by computeFrom/infrastructure mappers
  }

  public static Transaction of(
      TransactionId id,
      TransactionType type,
      Ticker ticker,
      BigDecimal quantity,
      Money pricePerUnit,
      Money totalAmount,
      LocalDate date,
      BigDecimal fees,
      String description,
      String currency,
      BigDecimal amountEur,
      BigDecimal eurFxRate) {
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

    if (fees != null && fees.compareTo(BigDecimal.ZERO) < 0) {
      throw new InvalidTransactionException("fees must not be negative");
    }
    BigDecimal safeFees = fees != null ? fees : BigDecimal.ZERO;

    if (currency == null || currency.isBlank())
      throw new InvalidTransactionException("currency must not be null or blank");
    if (amountEur == null || amountEur.compareTo(BigDecimal.ZERO) < 0)
      throw new InvalidTransactionException("amountEur must not be null and must be >= 0");
    if (eurFxRate == null || eurFxRate.compareTo(BigDecimal.ZERO) <= 0)
      throw new InvalidTransactionException("eurFxRate must be > 0");

    return new Transaction(
        id,
        type,
        ticker,
        quantity,
        pricePerUnit,
        totalAmount,
        date,
        safeFees,
        description,
        currency,
        amountEur,
        eurFxRate);
  }

  /** Convenience factory for EUR transactions — eurFxRate=1.0, amountEur=totalAmount. */
  public static Transaction ofEur(
      TransactionId id,
      TransactionType type,
      Ticker ticker,
      BigDecimal quantity,
      Money pricePerUnit,
      Money totalAmount,
      LocalDate date,
      BigDecimal fees,
      String description) {
    return Transaction.of(
        id,
        type,
        ticker,
        quantity,
        pricePerUnit,
        totalAmount,
        date,
        fees,
        description,
        "EUR",
        totalAmount != null ? totalAmount.amount() : null,
        BigDecimal.ONE);
  }
}
