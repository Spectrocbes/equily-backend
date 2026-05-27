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
import com.equily.portfolio.domain.exception.AccountNotFoundException;
import com.equily.shared.Country;
import java.util.List;
import java.util.Map;
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
}
