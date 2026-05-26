package com.equily.portfolio.application;

import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.FinancialAccountRepository;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.exception.AccountNotFoundException;
import java.util.List;
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
            command.date());

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
}
