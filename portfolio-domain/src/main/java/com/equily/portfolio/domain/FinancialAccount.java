package com.equily.portfolio.domain;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.account.AccountStatus;
import com.equily.portfolio.domain.account.AccountSubType;
import com.equily.portfolio.domain.exception.AccountClosedException;
import com.equily.portfolio.domain.exception.InsufficientFundsException;
import com.equily.portfolio.domain.exception.InvalidFinancialAccountException;
import com.equily.portfolio.domain.exception.InvalidHoldingException;
import com.equily.portfolio.domain.exception.InvalidTransactionException;
import com.equily.portfolio.domain.exception.TransactionNotFoundException;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class FinancialAccount {

  public record AssetInfo(AssetType assetType, AssetMetadata metadata) {}

  private static final Map<TransactionType, Integer> TYPE_PRIORITY =
      Map.of(
          TransactionType.DEPOSIT, 1,
          TransactionType.TRANSFER, 1,
          TransactionType.WITHDRAWAL, 2,
          TransactionType.PAYMENT, 2,
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
  private AccountStatus status;
  private LocalDate closedAt;
  private UUID linkedCheckingAccountId;

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
   * <p><strong>FOR INFRASTRUCTURE USE ONLY.</strong>
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
      LocalDate openedAt,
      AccountStatus status,
      LocalDate closedAt,
      UUID linkedCheckingAccountId) {
    FinancialAccount account =
        new FinancialAccount(id, name, accountType, balance, broker, ownerId, subType, openedAt);
    account.transactions.addAll(transactions);
    account.status = status != null ? status : AccountStatus.ACTIVE;
    account.closedAt = closedAt;
    account.linkedCheckingAccountId = linkedCheckingAccountId;
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
    Money zero = new Money(BigDecimal.ZERO, initialBalance.currency());
    FinancialAccount account =
        new FinancialAccount(
            FinancialAccountId.generate(),
            name,
            accountType,
            zero,
            broker,
            ownerId,
            subType,
            openedAt);
    account.status = AccountStatus.ACTIVE;
    account.closedAt = null;
    account.linkedCheckingAccountId = null;
    return account;
  }

  public void recordTransaction(Transaction t) {
    if (isClosed()) {
      throw new AccountClosedException(this.id());
    }
    validateTransactionTypeForAccount(t.type());
    validateTransactionDate(t.date());
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
      case WITHDRAWAL, PAYMENT -> {
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
      case TRANSFER -> {
        if (t.transferDirection() == TransferDirection.INCOMING) {
          balance = balance.add(t.totalAmount());
        } else {
          Money newBalance = balance.subtract(t.totalAmount());
          if (newBalance.amount().compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientFundsException(t.totalAmount(), balance);
          }
          balance = newBalance;
        }
      }
    }
    transactions.add(t);
  }

  private void validateTransactionDate(LocalDate date) {
    if (openedAt != null && date.isBefore(openedAt)) {
      throw new InvalidTransactionException(
          "Transaction date " + date + " cannot be before account opening date " + openedAt);
    }
  }

  private void validateTransactionTypeForAccount(TransactionType type) {
    if (!allowedTransactionTypes().contains(type)) {
      throw new InvalidTransactionException(
          "Transaction type " + type + " is not allowed for account type " + this.accountType());
    }
  }

  private Set<TransactionType> allowedTransactionTypes() {
    return switch (this.accountType) {
      case CASH_ACCOUNT, REAL_ESTATE -> EnumSet.allOf(TransactionType.class);
      default -> EnumSet.complementOf(EnumSet.of(TransactionType.PAYMENT));
    };
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

  public AccountStatus status() {
    return status;
  }

  public LocalDate closedAt() {
    return closedAt;
  }

  public UUID linkedCheckingAccountId() {
    return linkedCheckingAccountId;
  }

  public void linkCheckingAccount(UUID id) {
    this.linkedCheckingAccountId = id;
  }

  public boolean isClosed() {
    return status == AccountStatus.CLOSED;
  }

  public void close(LocalDate closedAt) {
    if (this.status == AccountStatus.CLOSED) {
      throw new IllegalStateException("Account is already closed");
    }
    this.status = AccountStatus.CLOSED;
    this.closedAt = closedAt;
  }

  public void deleteTransaction(TransactionId transactionId) {
    if (isClosed()) {
      throw new AccountClosedException(this.id());
    }

    transactions.stream()
        .filter(t -> t.id().equals(transactionId))
        .findFirst()
        .orElseThrow(() -> new TransactionNotFoundException(transactionId));

    List<Transaction> remaining =
        transactions.stream()
            .filter(t -> !t.id().equals(transactionId))
            .collect(Collectors.toCollection(ArrayList::new));

    validateChronology(remaining);

    this.transactions = remaining;
    this.balance = computeBalanceFrom(remaining);
  }

  public void updateTransaction(TransactionId id, UpdatedTransactionValues values) {
    Transaction existing =
        transactions.stream()
            .filter(t -> t.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new TransactionNotFoundException(id));

    BigDecimal updatedAmountEur = values.totalAmount().amount();
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
            values.description(),
            existing.currency(),
            updatedAmountEur,
            existing.eurFxRate(),
            existing.liquidationValueAtWithdrawal(),
            existing.grossWithdrawalAmount(),
            existing.transferId(),
            existing.linkedAccountId(),
            existing.externalAddress(),
            existing.transferDirection());

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
      case WITHDRAWAL, BUY, PAYMENT -> bal.subtract(tx.totalAmount());
      case TRANSFER ->
          tx.transferDirection() == TransferDirection.INCOMING
              ? bal.add(tx.totalAmount())
              : bal.subtract(tx.totalAmount());
    };
  }
}
