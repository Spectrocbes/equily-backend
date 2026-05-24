package com.equily.portfolio.domain;

import com.equily.portfolio.domain.exception.InsufficientFundsException;
import com.equily.portfolio.domain.exception.InvalidFinancialAccountException;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class FinancialAccount {

  public record AssetInfo(AssetType assetType, AssetMetadata metadata) {}

  private final FinancialAccountId id;
  private final String name;
  private final AccountType accountType;
  private Money balance;
  private final List<Transaction> transactions;

  private FinancialAccount(
      FinancialAccountId id, String name, AccountType accountType, Money balance) {
    this.id = id;
    this.name = name;
    this.accountType = accountType;
    this.balance = balance;
    this.transactions = new ArrayList<>();
  }

  public static FinancialAccount open(String name, AccountType accountType, Money initialBalance) {
    if (name == null || name.isBlank()) {
      throw new InvalidFinancialAccountException("name must not be null or blank");
    }
    if (accountType == null) {
      throw new InvalidFinancialAccountException("accountType must not be null");
    }
    if (initialBalance == null) {
      throw new InvalidFinancialAccountException("initialBalance must not be null");
    }
    return new FinancialAccount(FinancialAccountId.generate(), name, accountType, initialBalance);
  }

  public void recordTransaction(Transaction t) {
    switch (t.type()) {
      case DEPOSIT, DIVIDEND, SELL -> balance = balance.add(t.totalAmount());
      case WITHDRAWAL -> {
        Money newBalance = balance.subtract(t.totalAmount());
        if (newBalance.amount().compareTo(BigDecimal.ZERO) < 0) {
          throw new InsufficientFundsException(
              "withdrawal of " + t.totalAmount() + " exceeds balance " + balance);
        }
        balance = newBalance;
      }
      case BUY -> {
        Money newBalance = balance.subtract(t.totalAmount());
        if (newBalance.amount().compareTo(BigDecimal.ZERO) < 0) {
          throw new InsufficientFundsException(
              "buy of " + t.totalAmount() + " exceeds balance " + balance);
        }
        balance = newBalance;
      }
    }
    transactions.add(t);
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
}
