package com.equily.portfolio.application;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.AccountType;
import com.equily.portfolio.domain.AssetMetadata;
import com.equily.portfolio.domain.AssetType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.FinancialAccountRepository;
import com.equily.portfolio.domain.Holding;
import com.equily.portfolio.domain.PeaWithdrawalSimulation;
import com.equily.portfolio.domain.Ticker;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.account.AccountBusinessRules;
import com.equily.portfolio.domain.account.AccountSubType;
import com.equily.portfolio.domain.csv.CsvImportResult;
import com.equily.portfolio.domain.exception.AccountNotFoundException;
import com.equily.portfolio.domain.exception.TransactionNotFoundException;
import com.equily.portfolio.domain.marketdata.EnrichedHolding;
import com.equily.portfolio.domain.marketdata.FxRatePort;
import com.equily.portfolio.domain.marketdata.MarketDataPort;
import com.equily.portfolio.domain.marketdata.Quote;
import com.equily.shared.Country;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class FinancialAccountService implements FinancialAccountUseCase {

  private static final Set<AccountType> INVESTMENT_ACCOUNT_TYPES =
      Set.of(
          AccountType.PEA,
          AccountType.PEA_PME,
          AccountType.COMPTE_TITRES,
          AccountType.PER,
          AccountType.ASSURANCE_VIE,
          AccountType.CRYPTO_WALLET);

  // French regulated accounts are always EUR — ignore any client-supplied currency
  private static final Set<AccountType> EUR_ONLY_TYPES =
      Set.of(
          AccountType.PEA,
          AccountType.PEA_PME,
          AccountType.SAVINGS_ACCOUNT,
          AccountType.PER,
          AccountType.ASSURANCE_VIE,
          AccountType.COMPTE_TITRES);

  private static final BigDecimal PS_RATE = new BigDecimal("0.186");
  private static final Currency EUR = Currency.getInstance("EUR");

  private static final Set<AccountSubType> EUR_ONLY_SUBTYPES =
      Set.of(
          AccountSubType.PEA,
          AccountSubType.PEA_PME,
          AccountSubType.LIVRET_A,
          AccountSubType.LDDS,
          AccountSubType.LDD,
          AccountSubType.LEP,
          AccountSubType.LIVRET_JEUNE,
          AccountSubType.PER,
          AccountSubType.ASSURANCE_VIE);

  private static boolean isEurOnly(FinancialAccount account) {
    return EUR_ONLY_TYPES.contains(account.accountType())
        || (account.subType() != null && EUR_ONLY_SUBTYPES.contains(account.subType()));
  }

  // DEPOSIT/WITHDRAWAL/DIVIDEND must precede asset operations within the same day
  // to avoid InsufficientFundsException when Boursobank exports newest-first
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

  private final FinancialAccountRepository repository;
  private final MarketDataPort marketDataPort;
  private final FxRatePort fxRatePort;
  private final PeaClosureUseCase peaClosureUseCase;

  FinancialAccountService(
      FinancialAccountRepository repository,
      MarketDataPort marketDataPort,
      FxRatePort fxRatePort,
      PeaClosureUseCase peaClosureUseCase) {
    this.repository = repository;
    this.marketDataPort = marketDataPort;
    this.fxRatePort = fxRatePort;
    this.peaClosureUseCase = peaClosureUseCase;
  }

  @Override
  public FinancialAccountId createAccount(CreateFinancialAccountCommand command) {
    List<FinancialAccount> existingAccounts = repository.findAllByOwnerId(command.ownerId());

    AccountBusinessRules.validateCardinality(command.subType(), existingAccounts);

    // Domain balance is always EUR — pass EUR zero so open() initialises the balance in EUR.
    // The native currency of the initial deposit is handled separately below.
    FinancialAccount account =
        FinancialAccount.open(
            command.name(),
            command.accountType(),
            new Money(BigDecimal.ZERO, Currency.getInstance("EUR")),
            command.broker(),
            command.ownerId(),
            command.subType(),
            command.openedAt());

    if (command.linkedCheckingAccountId() != null) {
      account.linkCheckingAccount(command.linkedCheckingAccountId());
    }

    if (command.initialBalance().amount().compareTo(BigDecimal.ZERO) > 0) {
      String currency = isEurOnly(account) ? "EUR" : command.currency();
      BigDecimal eurFxRate =
          "EUR".equals(currency)
              ? BigDecimal.ONE
              : fxRatePort.getRateToEur(currency, LocalDate.now()).orElse(BigDecimal.ONE);
      BigDecimal amountEur =
          command.initialBalance().amount().multiply(eurFxRate).setScale(4, RoundingMode.HALF_EVEN);

      if (account.subType() != null) {
        List<FinancialAccount> accountsForValidation = new ArrayList<>(existingAccounts);
        accountsForValidation.add(account);
        AccountBusinessRules.validateDeposit(
            account, new Money(amountEur, EUR), accountsForValidation);
      }

      Money domainAmount = new Money(amountEur, Currency.getInstance("EUR"));
      Transaction initialTx =
          Transaction.of(
              TransactionId.generate(),
              TransactionType.DEPOSIT,
              null,
              null,
              null,
              domainAmount,
              LocalDate.now(),
              BigDecimal.ZERO,
              "Initial deposit",
              currency,
              amountEur,
              eurFxRate,
              null,
              null,
              null,
              null,
              null,
              null);
      account.recordTransaction(initialTx);
    }

    repository.save(account);
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

    if (command.type() == TransactionType.WITHDRAWAL
        && isPeaAccount(account)
        && AccountBusinessRules.isPeaOlderThan5Years(account)) {
      applyPeaWithdrawalOver5Years(account, command);
      return;
    }

    String currency =
        isEurOnly(account) ? "EUR" : (command.currency() != null ? command.currency() : "EUR");

    BigDecimal eurFxRate =
        "EUR".equals(currency)
            ? BigDecimal.ONE
            : fxRatePort.getRateToEur(currency, command.date()).orElse(BigDecimal.ONE);

    BigDecimal amountEur =
        command.totalAmount().amount().multiply(eurFxRate).setScale(4, RoundingMode.HALF_EVEN);

    if (command.type() == TransactionType.DEPOSIT && account.subType() != null) {
      List<FinancialAccount> allUserAccounts = repository.findAllByOwnerId(account.ownerId());
      AccountBusinessRules.validateDeposit(
          account, new Money(amountEur, Currency.getInstance("EUR")), allUserAccounts);
    }

    Money domainTotalAmount = new Money(amountEur, Currency.getInstance("EUR"));

    Money domainPricePerUnit = null;
    if (command.pricePerUnit() != null) {
      BigDecimal priceInEur =
          command.pricePerUnit().amount().multiply(eurFxRate).setScale(4, RoundingMode.HALF_EVEN);
      domainPricePerUnit = new Money(priceInEur, Currency.getInstance("EUR"));
    }

    BigDecimal feesInEur =
        command.fees() != null
            ? command.fees().multiply(eurFxRate).setScale(2, RoundingMode.HALF_EVEN)
            : BigDecimal.ZERO;

    Transaction transaction =
        Transaction.of(
            TransactionId.generate(),
            command.type(),
            command.ticker(),
            command.quantity(),
            domainPricePerUnit,
            domainTotalAmount,
            command.date(),
            feesInEur,
            command.description(),
            currency,
            amountEur,
            eurFxRate,
            null,
            null,
            null,
            null,
            command.externalAddress(),
            null);

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
  public List<EnrichedHolding> getEnrichedHoldings(
      FinancialAccountId id, UserId ownerId, String targetCurrency) {
    FinancialAccount account = getAccountById(id, ownerId);
    List<Holding> holdings = Holding.computeFrom(account.transactions());

    if (holdings.isEmpty()) return List.of();

    List<String> symbols = holdings.stream().map(h -> h.ticker().symbol()).distinct().toList();

    Map<String, Quote> quotes = marketDataPort.getQuotes(symbols);

    BigDecimal costToTarget =
        "EUR".equals(targetCurrency)
            ? BigDecimal.ONE
            : fxRatePort.getRate("EUR", targetCurrency).orElse(BigDecimal.ONE);

    return holdings.stream()
        .map(
            h -> {
              Quote q = quotes.get(h.ticker().symbol());
              if (q == null) return EnrichedHolding.withoutPrice(h);
              BigDecimal liveToTarget =
                  q.currency().equals(targetCurrency)
                      ? BigDecimal.ONE
                      : fxRatePort.getRate(q.currency(), targetCurrency).orElse(BigDecimal.ONE);
              return EnrichedHolding.withPrice(h, q, targetCurrency, liveToTarget, costToTarget);
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
  public void deleteTransaction(
      FinancialAccountId accountId, TransactionId transactionId, UserId userId) {
    FinancialAccount account = getAccountById(accountId, userId);
    account.deleteTransaction(transactionId);
    repository.save(account);
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

  @Override
  @Transactional(readOnly = true)
  public List<AccountPortfolioSummary> getPortfolioSummaries(UserId userId, String targetCurrency) {
    List<FinancialAccount> investmentAccounts =
        repository.findAllByOwnerId(userId).stream()
            .filter(a -> INVESTMENT_ACCOUNT_TYPES.contains(a.accountType()))
            .toList();

    if (investmentAccounts.isEmpty()) return List.of();

    List<String> allSymbols =
        investmentAccounts.stream()
            .flatMap(a -> Holding.computeFrom(a.transactions()).stream())
            .map(h -> h.ticker().symbol())
            .distinct()
            .toList();

    Map<String, Quote> quotes =
        allSymbols.isEmpty() ? Map.of() : marketDataPort.getQuotes(allSymbols);

    BigDecimal costToTarget =
        "EUR".equals(targetCurrency)
            ? BigDecimal.ONE
            : fxRatePort.getRate("EUR", targetCurrency).orElse(BigDecimal.ONE);

    return investmentAccounts.stream()
        .map(a -> computeSummary(a, quotes, targetCurrency, costToTarget))
        .toList();
  }

  private AccountPortfolioSummary computeSummary(
      FinancialAccount account,
      Map<String, Quote> quotes,
      String targetCurrency,
      BigDecimal costToTarget) {
    List<Holding> holdings = Holding.computeFrom(account.transactions());

    if (holdings.isEmpty()) {
      return new AccountPortfolioSummary(
          account.id(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
    }

    BigDecimal liveValue = BigDecimal.ZERO;
    BigDecimal costValue = BigDecimal.ZERO;
    boolean allPrices = true;

    for (Holding h : holdings) {
      Quote quote = quotes.get(h.ticker().symbol());
      costValue =
          costValue.add(
              h.averageCostPrice()
                  .amount()
                  .multiply(h.quantity())
                  .multiply(costToTarget)
                  .setScale(2, RoundingMode.HALF_EVEN));
      if (quote != null) {
        BigDecimal liveToTarget =
            quote.currency().equals(targetCurrency)
                ? BigDecimal.ONE
                : fxRatePort.getRate(quote.currency(), targetCurrency).orElse(BigDecimal.ONE);
        liveValue =
            liveValue.add(
                quote
                    .price()
                    .multiply(liveToTarget)
                    .multiply(h.quantity())
                    .setScale(2, RoundingMode.HALF_EVEN));
      } else {
        liveValue =
            liveValue.add(
                h.averageCostPrice()
                    .amount()
                    .multiply(h.quantity())
                    .multiply(costToTarget)
                    .setScale(2, RoundingMode.HALF_EVEN));
        allPrices = false;
      }
    }

    BigDecimal pnl = liveValue.subtract(costValue).setScale(2, RoundingMode.HALF_EVEN);
    BigDecimal pnlPct =
        costValue.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : pnl.divide(costValue, 4, RoundingMode.HALF_EVEN)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_EVEN);

    return new AccountPortfolioSummary(account.id(), liveValue, costValue, pnl, pnlPct, allPrices);
  }

  @Override
  @Transactional(readOnly = true)
  public PeaWithdrawalSimulation simulatePeaClosure(
      FinancialAccountId id, UserId userId, BigDecimal withdrawalAmount) {
    FinancialAccount account = getAccountById(id, userId);
    BigDecimal livePortfolioValue = computeLivePortfolioValue(account);
    return peaClosureUseCase.simulate(id, userId, withdrawalAmount, livePortfolioValue);
  }

  @Override
  public void closePea(FinancialAccountId id, UserId userId) {
    FinancialAccount account = getAccountById(id, userId);
    BigDecimal livePortfolioValue = computeLivePortfolioValue(account);
    peaClosureUseCase.closePea(id, userId, livePortfolioValue);
  }

  private static boolean isPeaAccount(FinancialAccount account) {
    return account.subType() == AccountSubType.PEA || account.subType() == AccountSubType.PEA_PME;
  }

  private void applyPeaWithdrawalOver5Years(
      FinancialAccount account, RecordTransactionCommand command) {

    BigDecimal withdrawalAmount = command.totalAmount().amount();
    BigDecimal livePortfolioValue = computeLivePortfolioValue(account);
    BigDecimal liquidationValue =
        livePortfolioValue.add(account.balance().amount()).setScale(2, RoundingMode.HALF_EVEN);

    BigDecimal totalDeposits = AccountBusinessRules.computeAdjustedTotalDeposits(account);

    BigDecimal gainGlobal = liquidationValue.subtract(totalDeposits).max(BigDecimal.ZERO);
    BigDecimal gainRatio =
        liquidationValue.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : gainGlobal.divide(liquidationValue, 6, RoundingMode.HALF_EVEN);

    BigDecimal taxableGain =
        withdrawalAmount.multiply(gainRatio).setScale(2, RoundingMode.HALF_EVEN);
    BigDecimal psTax = taxableGain.multiply(PS_RATE).setScale(2, RoundingMode.HALF_EVEN);
    BigDecimal netAmount = withdrawalAmount.subtract(psTax);

    BigDecimal feesInEur = command.fees() != null ? command.fees() : BigDecimal.ZERO;

    // Store liquidationValue and grossWithdrawalAmount so subsequent simulations can replay
    // the Loi Pacte versements counter correctly without approximation.
    account.recordTransaction(
        Transaction.of(
            TransactionId.generate(),
            TransactionType.WITHDRAWAL,
            null,
            null,
            null,
            new Money(netAmount, EUR),
            command.date(),
            feesInEur,
            command.description() != null ? command.description() : "PEA withdrawal (after PS)",
            "EUR",
            netAmount,
            BigDecimal.ONE,
            liquidationValue,
            withdrawalAmount,
            null,
            null,
            null,
            null));

    if (psTax.compareTo(BigDecimal.ZERO) > 0) {
      account.recordTransaction(
          Transaction.ofEur(
              TransactionId.generate(),
              TransactionType.WITHDRAWAL,
              null,
              null,
              null,
              new Money(psTax, EUR),
              command.date(),
              BigDecimal.ZERO,
              String.format(
                  "PS tax on PEA withdrawal (%.2f%% of %.2f€ gain)",
                  gainRatio.multiply(BigDecimal.valueOf(100)), taxableGain)));
    }

    repository.save(account);
  }

  private BigDecimal computeLivePortfolioValue(FinancialAccount account) {
    List<Holding> holdings = Holding.computeFrom(account.transactions());
    if (holdings.isEmpty()) return BigDecimal.ZERO;

    List<String> symbols = holdings.stream().map(h -> h.ticker().symbol()).distinct().toList();
    Map<String, Quote> quotes = marketDataPort.getQuotes(symbols);

    return holdings.stream()
        .map(
            h -> {
              Quote q = quotes.get(h.ticker().symbol());
              BigDecimal price = q != null ? q.price() : h.averageCostPrice().amount();
              return price.multiply(h.quantity()).setScale(2, RoundingMode.HALF_EVEN);
            })
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private String duplicateKey(LocalDate date, Ticker ticker, BigDecimal amount) {
    return date
        + "|"
        + (ticker != null ? ticker.symbol() : "null")
        + "|"
        + amount.stripTrailingZeros().toPlainString();
  }
}
