package com.equily.portfolio.domain;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.account.AccountSubType;
import com.equily.portfolio.domain.exception.InsufficientFundsException;
import com.equily.portfolio.domain.exception.InvalidFinancialAccountException;
import com.equily.portfolio.domain.exception.InvalidHoldingException;
import com.equily.portfolio.domain.exception.TransactionNotFoundException;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class FinancialAccount {

  public record AssetInfo(AssetType assetType, AssetMetadata metadata) {}

  private static final Map<TransactionType, Integer> TYPE_PRIORITY =
      Map.of(
          TransactionType.DEPOSIT, 1,
          TransactionType.WITHDRAWAL, 2,
          TransactionType.DIVIDEND, 3,
          TransactionType.INTEREST, 3,
          TransactionType.BUY, 4,
          TransactionType.SELL, 5);

  private final FinancialAccountId id;
  private final String name;
  private final AccountType accountType;
  private Money balance;
  private List<Transaction> transactions;
  private final String broker;
  private final UserId ownerId;
  private final AccountSubType subType;
  private final LocalDate openedAt;

  private FinancialAccount(
      FinancialAccountId id,
      String name,
      AccountType accountType,
      Money balance,
      String broker,
      UserId ownerId,
      AccountSubType subType,
      LocalDate openedAt) {
    this.id = id;
    this.name = name;
    this.accountType = accountType;
    this.balance = balance;
    this.broker = broker;
    this.ownerId = ownerId;
    this.subType = subType;
    this.openedAt = openedAt;
    this.transactions = new ArrayList<>();
  }

  /**
   * Rebuilds a FinancialAccount from persisted state (DB round-trip). Does NOT replay transactions
   * through recordTransaction() — the stored balance is authoritative.
   *
   * <p><strong>FOR INFRASTRUCTURE USE ONLY.</strong> Must only be called from {@code
   * portfolio-infrastructure} repository adapters. Never call this from application or web layers —
   * use {@link #open} instead.
   */
  public static FinancialAccount reconstruct(
      FinancialAccountId id,
      String name,
      AccountType accountType,
      Money balance,
      List<Transaction> transactions,
      String broker,
      UserId ownerId,
      AccountSubType subType,
      LocalDate openedAt) {
    FinancialAccount account =
        new FinancialAccount(id, name, accountType, balance, broker, ownerId, subType, openedAt);
    account.transactions.addAll(transactions);
    return account;
  }

  public static FinancialAccount open(
      String name,
      AccountType accountType,
      Money initialBalance,
      String broker,
      UserId ownerId,
      AccountSubType subType,
      LocalDate openedAt) {
    if (name == null || name.isBlank()) {
      throw new InvalidFinancialAccountException("name must not be null or blank");
    }
    if (accountType == null) {
      throw new InvalidFinancialAccountException("accountType must not be null");
    }
    if (initialBalance == null) {
      throw new InvalidFinancialAccountException("initialBalance must not be null");
    }
    if (broker == null || broker.isBlank()) {
      throw new InvalidFinancialAccountException("broker must not be null or blank");
    }
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(openedAt, "openedAt must not be null");
    // Account always starts at zero — initial balance is recorded as a DEPOSIT transaction
    Money zero = new Money(BigDecimal.ZERO, initialBalance.currency());
    return new FinancialAccount(
        FinancialAccountId.generate(), name, accountType, zero, broker, ownerId, subType, openedAt);
  }

  public void recordTransaction(Transaction t) {
    switch (t.type()) {
      case DEPOSIT, DIVIDEND, INTEREST -> balance = balance.add(t.totalAmount());
      case SELL -> {
        if (t.ticker() != null && t.quantity() != null) {
          BigDecimal heldQty = computeNetQuantity(t.ticker());
          if (t.quantity().compareTo(heldQty) > 0) {
            throw new InvalidHoldingException(t.ticker().symbol(), t.quantity(), heldQty);
          }
        }
        balance = balance.add(t.totalAmount());
      }
      case WITHDRAWAL -> {
        Money newBalance = balance.subtract(t.totalAmount());
        if (newBalance.amount().compareTo(BigDecimal.ZERO) < 0) {
          throw new InsufficientFundsException(t.totalAmount(), balance);
        }
        balance = newBalance;
      }
      case BUY -> {
        Money newBalance = balance.subtract(t.totalAmount());
        if (newBalance.amount().compareTo(BigDecimal.ZERO) < 0) {
          throw new InsufficientFundsException(t.totalAmount(), balance);
        }
        balance = newBalance;
      }
    }
    transactions.add(t);
  }

  private BigDecimal computeNetQuantity(Ticker ticker) {
    BigDecimal qty = BigDecimal.ZERO;
    for (Transaction t : transactions) {
      if (!ticker.equals(t.ticker())) continue;
      if (t.type() == TransactionType.BUY) qty = qty.add(t.quantity());
      else if (t.type() == TransactionType.SELL) qty = qty.subtract(t.quantity());
    }
    return qty;
  }

  // TODO: AssetInfo will be injected from MarketDataContext once that bounded context exists.
  // The Map<Ticker, AssetInfo> parameter is a deliberate temporary coupling.
  public List<Holding> getHoldings(Map<Ticker, AssetInfo> assetInfoByTicker) {
    Map<Ticker, List<Transaction>> byTicker =
        transactions.stream()
            .filter(t -> t.ticker() != null)
            .collect(Collectors.groupingBy(Transaction::ticker));

    List<Holding> holdings = new ArrayList<>();
    for (Map.Entry<Ticker, List<Transaction>> entry : byTicker.entrySet()) {
      Ticker ticker = entry.getKey();
      AssetInfo info = assetInfoByTicker.get(ticker);
      if (info == null) continue;
      Optional<Holding> holding =
          Holding.computeFrom(entry.getValue(), info.assetType(), info.metadata());
      holding.ifPresent(holdings::add);
    }
    return Collections.unmodifiableList(holdings);
  }

  public List<Transaction> transactions() {
    return Collections.unmodifiableList(transactions);
  }

  public FinancialAccountId id() {
    return id;
  }

  public String name() {
    return name;
  }

  public AccountType accountType() {
    return accountType;
  }

  public Money balance() {
    return balance;
  }

  public String broker() {
    return broker;
  }

  public UserId ownerId() {
    return ownerId;
  }

  public AccountSubType subType() {
    return subType;
  }

  public LocalDate openedAt() {
    return openedAt;
  }

  public void updateTransaction(TransactionId id, UpdatedTransactionValues values) {
    Transaction existing =
        transactions.stream()
            .filter(t -> t.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new TransactionNotFoundException(id));

    Transaction updated =
        Transaction.of(
            existing.id(),
            existing.type(),
            existing.ticker(),
            values.quantity(),
            values.pricePerUnit(),
            values.totalAmount(),
            values.date(),
            values.fees(),
            values.description());

    List<Transaction> newList =
        transactions.stream()
            .map(t -> t.id().equals(id) ? updated : t)
            .collect(Collectors.toCollection(ArrayList::new));

    validateChronology(newList);

    this.transactions = newList;
    this.balance = computeBalanceFrom(newList);
  }

  private void validateChronology(List<Transaction> txList) {
    List<Transaction> sorted =
        txList.stream()
            .sorted(
                Comparator.comparing(Transaction::date)
                    .thenComparingInt(t -> TYPE_PRIORITY.getOrDefault(t.type(), 99)))
            .toList();

    Money running = new Money(BigDecimal.ZERO, this.balance.currency());
    for (Transaction tx : sorted) {
      running = applyToBalance(running, tx);
      if (running.amount().compareTo(BigDecimal.ZERO) < 0) {
        throw new InsufficientFundsException(tx.totalAmount(), running.add(tx.totalAmount()));
      }
    }
  }

  private Money computeBalanceFrom(List<Transaction> txList) {
    Money zero = new Money(BigDecimal.ZERO, this.balance.currency());
    return txList.stream()
        .reduce(
            zero,
            (bal, tx) -> applyToBalance(bal, tx),
            (a, b) -> {
              throw new UnsupportedOperationException();
            });
  }

  private Money applyToBalance(Money bal, Transaction tx) {
    return switch (tx.type()) {
      case DEPOSIT, DIVIDEND, INTEREST, SELL -> bal.add(tx.totalAmount());
      case WITHDRAWAL, BUY -> bal.subtract(tx.totalAmount());
    };
  }
}
