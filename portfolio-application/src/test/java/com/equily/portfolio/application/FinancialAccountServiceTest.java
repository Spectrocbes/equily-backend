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
import com.equily.portfolio.domain.Holding;
import com.equily.portfolio.domain.Ticker;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.csv.CsvImportResult;
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

  @Test
  void getHoldings_returns_holdings_for_account_with_buy_transactions() {
    FinancialAccount account =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(new BigDecimal("10000"), Currency.getInstance("EUR")),
            "Fortuneo");
    Transaction buy =
        Transaction.of(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            new BigDecimal("10"),
            new Money(new BigDecimal("150.00"), Currency.getInstance("EUR")),
            new Money(new BigDecimal("1500.00"), Currency.getInstance("EUR")),
            LocalDate.now(),
            BigDecimal.ZERO,
            null);
    account.recordTransaction(buy);

    when(repository.findById(any())).thenReturn(Optional.of(account));

    List<Holding> holdings = service.getHoldings(FinancialAccountId.generate());

    assertThat(holdings).hasSize(1);
    assertThat(holdings.get(0).ticker().symbol()).isEqualTo("AAPL");
    assertThat(holdings.get(0).quantity()).isEqualByComparingTo(new BigDecimal("10"));
    assertThat(holdings.get(0).averageCostPrice().amount())
        .isEqualByComparingTo(new BigDecimal("150.00"));
  }

  @Test
  void getHoldings_throws_AccountNotFoundException_when_account_not_found() {
    when(repository.findById(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getHoldings(FinancialAccountId.generate()))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void getHoldings_returns_empty_list_when_account_has_no_ticker_transactions() {
    FinancialAccount account =
        FinancialAccount.open(
            "Mon PEA", AccountType.PEA, new Money(new BigDecimal("5000"), EUR), "Fortuneo");
    Transaction deposit =
        Transaction.of(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("500.00"), EUR),
            LocalDate.of(2026, 5, 27),
            null,
            null);
    account.recordTransaction(deposit);
    when(repository.findById(any())).thenReturn(Optional.of(account));

    List<Holding> holdings = service.getHoldings(FinancialAccountId.generate());

    assertThat(holdings).isEmpty();
  }

  @Test
  void importCsv_imports_new_transactions() {
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA", AccountType.PEA, new Money(BigDecimal.valueOf(10000), EUR), "BoursoBank");
    when(repository.findById(any())).thenReturn(Optional.of(account));

    Transaction newTx =
        Transaction.of(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("500"), EUR),
            LocalDate.of(2026, 1, 15),
            BigDecimal.ZERO,
            "Imported from Boursobank");
    CsvImportResult parsed = new CsvImportResult(1, 0, 0, List.of(), List.of(newTx));

    CsvImportResult result = service.importCsv(FinancialAccountId.generate(), parsed);

    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.skipped()).isZero();
    verify(repository).save(account);
  }

  @Test
  void importCsv_skips_duplicate_transactions() {
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA", AccountType.PEA, new Money(BigDecimal.valueOf(10000), EUR), "BoursoBank");
    Transaction existingDeposit =
        Transaction.of(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("3700"), EUR),
            LocalDate.of(2026, 1, 29),
            BigDecimal.ZERO,
            null);
    account.recordTransaction(existingDeposit);
    when(repository.findById(any())).thenReturn(Optional.of(account));

    Transaction duplicateTx =
        Transaction.of(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("3700"), EUR),
            LocalDate.of(2026, 1, 29),
            BigDecimal.ZERO,
            "Imported from Boursobank");
    CsvImportResult parsed = new CsvImportResult(1, 0, 0, List.of(), List.of(duplicateTx));

    CsvImportResult result = service.importCsv(FinancialAccountId.generate(), parsed);

    assertThat(result.imported()).isZero();
    assertThat(result.skipped()).isEqualTo(1);
    verify(repository).save(account);
  }

  @Test
  void importCsv_throws_AccountNotFoundException_when_account_missing() {
    when(repository.findById(any())).thenReturn(Optional.empty());
    CsvImportResult parsed = new CsvImportResult(0, 0, 0, List.of(), List.of());

    assertThatThrownBy(() -> service.importCsv(FinancialAccountId.generate(), parsed))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void importCsv_deduplicates_within_same_file() {
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA", AccountType.PEA, new Money(BigDecimal.valueOf(10000), EUR), "BoursoBank");
    when(repository.findById(any())).thenReturn(Optional.of(account));

    Transaction tx1 =
        Transaction.of(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("500"), EUR),
            LocalDate.of(2026, 1, 15),
            BigDecimal.ZERO,
            "Imported from Boursobank");
    Transaction tx2 =
        Transaction.of(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("500"), EUR),
            LocalDate.of(2026, 1, 15),
            BigDecimal.ZERO,
            "Imported from Boursobank");
    CsvImportResult parsed = new CsvImportResult(2, 0, 0, List.of(), List.of(tx1, tx2));

    CsvImportResult result = service.importCsv(FinancialAccountId.generate(), parsed);

    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.skipped()).isEqualTo(1);
  }

  @Test
  void getHoldings_merges_multiple_buys_same_ticker_into_one_holding() {
    FinancialAccount account =
        FinancialAccount.open(
            "Mon PEA", AccountType.PEA, new Money(new BigDecimal("10000"), EUR), "Fortuneo");
    Transaction buy1 =
        Transaction.of(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            new BigDecimal("5"),
            new Money(new BigDecimal("100.00"), EUR),
            new Money(new BigDecimal("500.00"), EUR),
            LocalDate.of(2026, 5, 1),
            BigDecimal.ZERO,
            null);
    Transaction buy2 =
        Transaction.of(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            new BigDecimal("5"),
            new Money(new BigDecimal("200.00"), EUR),
            new Money(new BigDecimal("1000.00"), EUR),
            LocalDate.of(2026, 5, 15),
            BigDecimal.ZERO,
            null);
    account.recordTransaction(buy1);
    account.recordTransaction(buy2);
    when(repository.findById(any())).thenReturn(Optional.of(account));

    List<Holding> holdings = service.getHoldings(FinancialAccountId.generate());

    assertThat(holdings).hasSize(1);
    assertThat(holdings.get(0).ticker().symbol()).isEqualTo("AAPL");
    assertThat(holdings.get(0).quantity()).isEqualByComparingTo(new BigDecimal("10"));
    assertThat(holdings.get(0).averageCostPrice().amount())
        .isEqualByComparingTo(new BigDecimal("150.00"));
  }
}
