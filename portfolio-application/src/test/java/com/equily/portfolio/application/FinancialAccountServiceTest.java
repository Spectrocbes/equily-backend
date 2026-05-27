package com.equily.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.equily.portfolio.domain.AccountType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.FinancialAccountRepository;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.exception.AccountNotFoundException;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancialAccountServiceTest {

  @Mock private FinancialAccountRepository repository;

  @InjectMocks private FinancialAccountService service;

  private static final Currency EUR = Currency.getInstance("EUR");

  @Test
  void createAccount_savesAccountAndReturnsId() {
    CreateFinancialAccountCommand command =
        new CreateFinancialAccountCommand(
            "My PEA", AccountType.PEA, new Money(BigDecimal.valueOf(1000), EUR), "Fortuneo");

    FinancialAccountId result = service.createAccount(command);

    assertThat(result).isNotNull();
    verify(repository).save(any(FinancialAccount.class));
  }

  @Test
  void recordTransaction_loadsAccountRecordsTxAndSaves() {
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA", AccountType.PEA, new Money(BigDecimal.valueOf(10000), EUR), "Fortuneo");
    FinancialAccountId accountId = account.id();
    when(repository.findById(accountId)).thenReturn(Optional.of(account));

    RecordTransactionCommand command =
        new RecordTransactionCommand(
            accountId,
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(500), EUR),
            LocalDate.of(2026, 5, 24),
            null,
            null);

    service.recordTransaction(command);

    verify(repository).findById(accountId);
    verify(repository).save(account);
  }

  @Test
  void recordTransaction_throwsAccountNotFoundWhenMissing() {
    FinancialAccountId id = FinancialAccountId.generate();
    when(repository.findById(id)).thenReturn(Optional.empty());

    RecordTransactionCommand command =
        new RecordTransactionCommand(
            id,
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(100), EUR),
            LocalDate.of(2026, 5, 24),
            null,
            null);

    assertThatThrownBy(() -> service.recordTransaction(command))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void getAllAccounts_delegatesToRepository() {
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA", AccountType.PEA, new Money(BigDecimal.valueOf(1000), EUR), "Fortuneo");
    when(repository.findAll()).thenReturn(List.of(account));

    List<FinancialAccount> result = service.getAllAccounts();

    assertThat(result).hasSize(1);
    verify(repository).findAll();
  }

  @Test
  void getAccountById_returnsAccountWhenFound() {
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA", AccountType.PEA, new Money(BigDecimal.valueOf(1000), EUR), "Fortuneo");
    FinancialAccountId id = account.id();
    when(repository.findById(id)).thenReturn(Optional.of(account));

    FinancialAccount result = service.getAccountById(id);

    assertThat(result.id()).isEqualTo(id);
    assertThat(result.name()).isEqualTo("My PEA");
  }

  @Test
  void getAccountById_throwsAccountNotFoundWhenMissing() {
    FinancialAccountId id = FinancialAccountId.generate();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getAccountById(id))
        .isInstanceOf(AccountNotFoundException.class)
        .hasMessageContaining(id.value().toString());
  }
}
