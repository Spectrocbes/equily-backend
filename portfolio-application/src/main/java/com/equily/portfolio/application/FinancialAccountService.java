package com.equily.portfolio.application;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.AssetMetadata;
import com.equily.portfolio.domain.AssetType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.FinancialAccountRepository;
import com.equily.portfolio.domain.Holding;
import com.equily.portfolio.domain.Ticker;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.account.AccountBusinessRules;
import com.equily.portfolio.domain.csv.CsvImportResult;
import com.equily.portfolio.domain.exception.AccountNotFoundException;
import com.equily.portfolio.domain.exception.TransactionNotFoundException;
import com.equily.portfolio.domain.marketdata.EnrichedHolding;
import com.equily.portfolio.domain.marketdata.MarketDataPort;
import com.equily.portfolio.domain.marketdata.Quote;
import com.equily.shared.Country;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class FinancialAccountService implements FinancialAccountUseCase {

  // DEPOSIT/WITHDRAWAL/DIVIDEND must precede asset operations within the same day
  // to avoid InsufficientFundsException when Boursobank exports newest-first
  private static final Map<TransactionType, Integer> TYPE_PRIORITY =
      Map.of(
          TransactionType.DEPOSIT, 1,
          TransactionType.WITHDRAWAL, 2,
          TransactionType.DIVIDEND, 3,
          TransactionType.INTEREST, 3,
          TransactionType.BUY, 4,
          TransactionType.SELL, 5);

  private final FinancialAccountRepository repository;
  private final MarketDataPort marketDataPort;

  FinancialAccountService(FinancialAccountRepository repository, MarketDataPort marketDataPort) {
    this.repository = repository;
    this.marketDataPort = marketDataPort;
  }

  @Override
  public FinancialAccountId createAccount(CreateFinancialAccountCommand command) {
    FinancialAccount account =
        FinancialAccount.open(
            command.name(),
            command.accountType(),
            command.initialBalance(),
            command.broker(),
            command.ownerId(),
            command.subType(),
            command.openedAt());
    repository.save(account);

    if (command.initialBalance().amount().compareTo(BigDecimal.ZERO) > 0) {
      Transaction initialDeposit =
          Transaction.of(
              TransactionId.generate(),
              TransactionType.DEPOSIT,
              null,
              null,
              null,
              command.initialBalance(),
              LocalDate.now(),
              BigDecimal.ZERO,
              "Initial deposit");
      account.recordTransaction(initialDeposit);
      repository.save(account);
    }

    return account.id();
  }

  @Override
  public void recordTransaction(RecordTransactionCommand command) {
    FinancialAccount account =
        repository
            .findById(command.accountId())
            .orElseThrow(() -> new AccountNotFoundException(command.accountId()));

    if (!account.ownerId().equals(command.userId())) {
      throw new AccountNotFoundException(command.accountId());
    }

    if (command.type() == TransactionType.DEPOSIT && account.subType() != null) {
      List<FinancialAccount> allUserAccounts = repository.findAllByOwnerId(account.ownerId());
      AccountBusinessRules.validateDeposit(account, command.totalAmount(), allUserAccounts);
    }

    Transaction transaction =
        Transaction.of(
            TransactionId.generate(),
            command.type(),
            command.ticker(),
            command.quantity(),
            command.pricePerUnit(),
            command.totalAmount(),
            command.date(),
            command.fees(),
            command.description());

    account.recordTransaction(transaction);
    repository.save(account);
  }

  @Override
  @Transactional(readOnly = true)
  public List<FinancialAccount> getAllAccounts(UserId ownerId) {
    return repository.findAllByOwnerId(ownerId);
  }

  @Override
  @Transactional(readOnly = true)
  public FinancialAccount getAccountById(FinancialAccountId id, UserId ownerId) {
    FinancialAccount account =
        repository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    if (!account.ownerId().equals(ownerId)) {
      throw new AccountNotFoundException(id);
    }
    return account;
  }

  @Override
  @Transactional(readOnly = true)
  public List<EnrichedHolding> getEnrichedHoldings(FinancialAccountId id, UserId ownerId) {
    FinancialAccount account = getAccountById(id, ownerId);
    List<Holding> holdings = Holding.computeFrom(account.transactions());

    if (holdings.isEmpty()) return List.of();

    List<String> symbols = holdings.stream().map(h -> h.ticker().symbol()).distinct().toList();

    Map<String, Quote> quotes = marketDataPort.getQuotes(symbols);

    return holdings.stream()
        .map(
            h -> {
              Quote q = quotes.get(h.ticker().symbol());
              return q != null ? EnrichedHolding.withPrice(h, q) : EnrichedHolding.withoutPrice(h);
            })
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Holding> getHoldings(FinancialAccountId id, UserId ownerId) {
    FinancialAccount account = getAccountById(id, ownerId);

    // Phase 1: AssetType defaults to STOCK, no metadata available yet
    // TODO: wire with MarketDataContext in Phase 2
    Map<Ticker, FinancialAccount.AssetInfo> assetInfoMap =
        account.transactions().stream()
            .filter(t -> t.ticker() != null)
            .collect(
                Collectors.toMap(
                    Transaction::ticker,
                    t ->
                        new FinancialAccount.AssetInfo(
                            AssetType.STOCK,
                            new AssetMetadata(t.ticker().symbol(), null, new Country("US"))),
                    (existing, replacement) -> existing));

    return account.getHoldings(assetInfoMap);
  }

  @Override
  @Transactional
  public CsvImportResult importCsv(
      FinancialAccountId accountId, CsvImportResult parsed, UserId ownerId) {
    FinancialAccount account = getAccountById(accountId, ownerId);

    Set<String> existingKeys =
        account.transactions().stream()
            .map(t -> duplicateKey(t.date(), t.ticker(), t.totalAmount().amount()))
            .collect(Collectors.toSet());

    List<Transaction> toImport = new ArrayList<>();
    int skipped = parsed.skipped();

    for (Transaction tx : parsed.transactions()) {
      String key = duplicateKey(tx.date(), tx.ticker(), tx.totalAmount().amount());
      if (existingKeys.contains(key)) {
        skipped++;
      } else {
        toImport.add(tx);
        existingKeys.add(key);
      }
    }

    // Sort ascending by date, then by type priority within the same day.
    // Boursobank exports newest-first; DEPOSIT must precede BUY on the same day.
    toImport.sort(
        Comparator.comparing(Transaction::date)
            .thenComparingInt(t -> TYPE_PRIORITY.getOrDefault(t.type(), 99)));

    toImport.forEach(account::recordTransaction);
    repository.save(account);

    return new CsvImportResult(
        toImport.size(), skipped, parsed.errors(), parsed.errorDetails(), toImport);
  }

  @Override
  @Transactional
  public void updateTransaction(UpdateTransactionCommand command) {
    FinancialAccount account =
        repository
            .findById(command.accountId())
            .orElseThrow(() -> new AccountNotFoundException(command.accountId()));

    if (!account.ownerId().equals(command.userId())) {
      throw new AccountNotFoundException(command.accountId());
    }

    Transaction existing =
        account.transactions().stream()
            .filter(t -> t.id().equals(command.transactionId()))
            .findFirst()
            .orElseThrow(() -> new TransactionNotFoundException(command.transactionId()));

    account.updateTransaction(command.transactionId(), command.values());

    if (existing.type() == TransactionType.DEPOSIT && account.subType() != null) {
      List<FinancialAccount> allUserAccounts = repository.findAllByOwnerId(account.ownerId());
      AccountBusinessRules.validateDepositAfterEdit(account, allUserAccounts);
    }

    repository.save(account);
  }

  @Override
  @Transactional(readOnly = true)
  public TransactionType getTransactionType(
      FinancialAccountId accountId, TransactionId transactionId, UserId userId) {
    FinancialAccount account =
        repository.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    if (!account.ownerId().equals(userId)) {
      throw new AccountNotFoundException(accountId);
    }
    return account.transactions().stream()
        .filter(t -> t.id().equals(transactionId))
        .map(Transaction::type)
        .findFirst()
        .orElseThrow(() -> new TransactionNotFoundException(transactionId));
  }

  private String duplicateKey(LocalDate date, Ticker ticker, BigDecimal amount) {
    return date
        + "|"
        + (ticker != null ? ticker.symbol() : "null")
        + "|"
        + amount.stripTrailingZeros().toPlainString();
  }
}
