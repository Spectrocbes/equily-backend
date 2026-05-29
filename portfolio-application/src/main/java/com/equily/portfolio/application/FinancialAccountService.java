package com.equily.portfolio.application;

import com.equily.portfolio.domain.AssetMetadata;
import com.equily.portfolio.domain.AssetType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.FinancialAccountRepository;
import com.equily.portfolio.domain.Holding;
import com.equily.portfolio.domain.Ticker;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.csv.CsvImportResult;
import com.equily.portfolio.domain.exception.AccountNotFoundException;
import com.equily.shared.Country;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class FinancialAccountService implements FinancialAccountUseCase {

  private final FinancialAccountRepository repository;

  FinancialAccountService(FinancialAccountRepository repository) {
    this.repository = repository;
  }

  @Override
  public FinancialAccountId createAccount(CreateFinancialAccountCommand command) {
    FinancialAccount account =
        FinancialAccount.open(
            command.name(), command.accountType(), command.initialBalance(), command.broker());
    repository.save(account);
    return account.id();
  }

  @Override
  public void recordTransaction(RecordTransactionCommand command) {
    FinancialAccount account =
        repository
            .findById(command.accountId())
            .orElseThrow(() -> new AccountNotFoundException(command.accountId()));

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
  public List<FinancialAccount> getAllAccounts() {
    return repository.findAll();
  }

  @Override
  @Transactional(readOnly = true)
  public FinancialAccount getAccountById(FinancialAccountId id) {
    return repository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
  }

  @Override
  @Transactional(readOnly = true)
  public List<Holding> getHoldings(FinancialAccountId id) {
    FinancialAccount account =
        repository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));

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
  public CsvImportResult importCsv(FinancialAccountId accountId, CsvImportResult parsed) {
    FinancialAccount account =
        repository.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));

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

    toImport.forEach(account::recordTransaction);
    repository.save(account);

    return new CsvImportResult(
        toImport.size(), skipped, parsed.errors(), parsed.errorDetails(), toImport);
  }

  private String duplicateKey(LocalDate date, Ticker ticker, BigDecimal amount) {
    return date
        + "|"
        + (ticker != null ? ticker.symbol() : "null")
        + "|"
        + amount.stripTrailingZeros().toPlainString();
  }
}
