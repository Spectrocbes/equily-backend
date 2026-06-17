package com.equily.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.AccountType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.FinancialAccountRepository;
import com.equily.portfolio.domain.Holding;
import com.equily.portfolio.domain.Ticker;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.UpdatedTransactionValues;
import com.equily.portfolio.domain.account.AccountSubType;
import com.equily.portfolio.domain.csv.CsvImportResult;
import com.equily.portfolio.domain.exception.AccountCardinalityException;
import com.equily.portfolio.domain.exception.AccountNotFoundException;
import com.equily.portfolio.domain.exception.DepositLimitExceededException;
import com.equily.portfolio.domain.exception.TransactionNotFoundException;
import com.equily.portfolio.domain.marketdata.EnrichedHolding;
import com.equily.portfolio.domain.marketdata.FxRatePort;
import com.equily.portfolio.domain.marketdata.MarketDataPort;
import com.equily.portfolio.domain.marketdata.Quote;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancialAccountServiceTest {

  @Mock private FinancialAccountRepository repository;
  @Mock private MarketDataPort marketDataPort;
  @Mock private FxRatePort fxRatePort;
  @Mock private PeaClosureUseCase peaClosureUseCase;

  @InjectMocks private FinancialAccountService service;

  private static final Currency EUR = Currency.getInstance("EUR");

  private static final LocalDate OPENED_AT = LocalDate.of(2024, 1, 1);

  private static FinancialAccount openAccount(String name, String balance) {
    return FinancialAccount.open(
        name,
        AccountType.PEA,
        new Money(BigDecimal.valueOf(Double.parseDouble(balance)), EUR),
        "Fortuneo",
        UserId.generate(),
        null,
        OPENED_AT);
  }

  @Test
  void createAccount_savesAccountAndReturnsId() {
    CreateFinancialAccountCommand command =
        new CreateFinancialAccountCommand(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            UserId.generate(),
            null,
            OPENED_AT,
            "EUR");

    FinancialAccountId result = service.createAccount(command);

    assertThat(result).isNotNull();
    verify(repository, times(1)).save(any(FinancialAccount.class));
  }

  @Test
  void createAccount_with_initial_balance_creates_deposit_transaction() {
    Money initialBalance = new Money(new BigDecimal("1000"), Currency.getInstance("EUR"));
    CreateFinancialAccountCommand command =
        new CreateFinancialAccountCommand(
            "Mon Livret A",
            AccountType.SAVINGS_ACCOUNT,
            initialBalance,
            "BNP",
            UserId.generate(),
            AccountSubType.LIVRET_A,
            OPENED_AT,
            "EUR");

    service.createAccount(command);

    verify(repository, times(1)).save(any(FinancialAccount.class));
  }

  @Test
  void createAccount_with_zero_balance_saves_once() {
    Money zero = new Money(BigDecimal.ZERO, Currency.getInstance("EUR"));
    CreateFinancialAccountCommand command =
        new CreateFinancialAccountCommand(
            "Mon CTO",
            AccountType.COMPTE_TITRES,
            zero,
            "Fortuneo",
            UserId.generate(),
            null,
            OPENED_AT,
            "EUR");

    service.createAccount(command);

    verify(repository, times(1)).save(any(FinancialAccount.class));
  }

  @Test
  void recordTransaction_loadsAccountRecordsTxAndSaves() {
    FinancialAccount account = openAccount("My PEA", "10000");
    FinancialAccountId accountId = account.id();
    when(repository.findById(accountId)).thenReturn(Optional.of(account));

    RecordTransactionCommand command =
        new RecordTransactionCommand(
            accountId,
            account.ownerId(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(500), EUR),
            LocalDate.of(2026, 5, 24),
            null,
            null,
            "EUR");

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
            UserId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(100), EUR),
            LocalDate.of(2026, 5, 24),
            null,
            null,
            "EUR");

    assertThatThrownBy(() -> service.recordTransaction(command))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void recordTransaction_throwsDepositLimitExceeded_when_livretA_full() {
    UserId ownerId = UserId.generate();
    FinancialAccount livretA =
        FinancialAccount.open(
            "Livret A",
            AccountType.PEA, // AccountType irrelevant for this rule
            new Money(BigDecimal.ZERO, EUR),
            "Test Bank",
            ownerId,
            AccountSubType.LIVRET_A,
            OPENED_AT);
    // Add deposits totaling 22000 EUR
    Transaction existingDeposit =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("22000"), EUR),
            LocalDate.of(2026, 1, 1),
            null,
            null);
    livretA.recordTransaction(existingDeposit);
    when(repository.findById(livretA.id())).thenReturn(Optional.of(livretA));
    when(repository.findAllByOwnerId(ownerId)).thenReturn(List.of(livretA));

    RecordTransactionCommand command =
        new RecordTransactionCommand(
            livretA.id(),
            ownerId,
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("1000"), EUR),
            LocalDate.of(2026, 5, 24),
            null,
            null,
            "EUR");

    assertThatThrownBy(() -> service.recordTransaction(command))
        .isInstanceOf(DepositLimitExceededException.class);
  }

  @Test
  void recordTransaction_throwsWhenOwnershipMismatch() {
    FinancialAccount account = openAccount("My PEA", "10000");
    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    RecordTransactionCommand command =
        new RecordTransactionCommand(
            account.id(),
            UserId.generate(), // different user
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(BigDecimal.valueOf(500), EUR),
            LocalDate.of(2026, 5, 24),
            null,
            null,
            "EUR");

    assertThatThrownBy(() -> service.recordTransaction(command))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void getAllAccounts_delegatesToRepository() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.valueOf(1000), EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    when(repository.findAllByOwnerId(ownerId)).thenReturn(List.of(account));

    List<FinancialAccount> result = service.getAllAccounts(ownerId);

    assertThat(result).hasSize(1);
    verify(repository).findAllByOwnerId(ownerId);
  }

  @Test
  void getAccountById_returnsAccountWhenFound() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.valueOf(1000), EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    FinancialAccountId id = account.id();
    when(repository.findById(id)).thenReturn(Optional.of(account));

    FinancialAccount result = service.getAccountById(id, ownerId);

    assertThat(result.id()).isEqualTo(id);
    assertThat(result.name()).isEqualTo("My PEA");
  }

  @Test
  void getAccountById_throwsAccountNotFoundWhenMissing() {
    FinancialAccountId id = FinancialAccountId.generate();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getAccountById(id, UserId.generate()))
        .isInstanceOf(AccountNotFoundException.class)
        .hasMessageContaining(id.value().toString());
  }

  @Test
  void getAccountById_throwsAccountNotFoundWhenOwnerMismatch() {
    UserId owner = UserId.generate();
    UserId otherUser = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.valueOf(1000), EUR),
            "Fortuneo",
            owner,
            null,
            OPENED_AT);
    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    assertThatThrownBy(() -> service.getAccountById(account.id(), otherUser))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void getHoldings_returns_holdings_for_account_with_buy_transactions() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, Currency.getInstance("EUR")),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), Currency.getInstance("EUR")),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    Transaction buy =
        Transaction.ofEur(
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

    List<Holding> holdings = service.getHoldings(account.id(), ownerId);

    assertThat(holdings).hasSize(1);
    assertThat(holdings.get(0).ticker().symbol()).isEqualTo("AAPL");
    assertThat(holdings.get(0).quantity()).isEqualByComparingTo(new BigDecimal("10"));
    assertThat(holdings.get(0).averageCostPrice().amount())
        .isEqualByComparingTo(new BigDecimal("150.00"));
  }

  @Test
  void getHoldings_throws_AccountNotFoundException_when_account_not_found() {
    when(repository.findById(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getHoldings(FinancialAccountId.generate(), UserId.generate()))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void getHoldings_returns_empty_list_when_account_has_no_ticker_transactions() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(new BigDecimal("5000"), EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    Transaction deposit =
        Transaction.ofEur(
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

    List<Holding> holdings = service.getHoldings(account.id(), ownerId);

    assertThat(holdings).isEmpty();
  }

  @Test
  void importCsv_imports_new_transactions() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.valueOf(10000), EUR),
            "BoursoBank",
            ownerId,
            null,
            OPENED_AT);
    when(repository.findById(any())).thenReturn(Optional.of(account));

    Transaction newTx =
        Transaction.ofEur(
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

    CsvImportResult result = service.importCsv(account.id(), parsed, ownerId);

    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.skipped()).isZero();
    verify(repository).save(account);
  }

  @Test
  void importCsv_skips_duplicate_transactions() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.valueOf(10000), EUR),
            "BoursoBank",
            ownerId,
            null,
            OPENED_AT);
    Transaction existingDeposit =
        Transaction.ofEur(
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
        Transaction.ofEur(
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

    CsvImportResult result = service.importCsv(account.id(), parsed, ownerId);

    assertThat(result.imported()).isZero();
    assertThat(result.skipped()).isEqualTo(1);
    verify(repository).save(account);
  }

  @Test
  void importCsv_throws_AccountNotFoundException_when_account_missing() {
    when(repository.findById(any())).thenReturn(Optional.empty());
    CsvImportResult parsed = new CsvImportResult(0, 0, 0, List.of(), List.of());

    assertThatThrownBy(
            () -> service.importCsv(FinancialAccountId.generate(), parsed, UserId.generate()))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void importCsv_deduplicates_within_same_file() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.valueOf(10000), EUR),
            "BoursoBank",
            ownerId,
            null,
            OPENED_AT);
    when(repository.findById(any())).thenReturn(Optional.of(account));

    Transaction tx1 =
        Transaction.ofEur(
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
        Transaction.ofEur(
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

    CsvImportResult result = service.importCsv(account.id(), parsed, ownerId);

    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.skipped()).isEqualTo(1);
  }

  @Test
  void importCsv_sorts_transactions_by_date_before_applying() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "BoursoBank",
            ownerId,
            null,
            OPENED_AT);
    when(repository.findById(any())).thenReturn(Optional.of(account));

    // Boursobank exports newest-first: BUY on day 2 comes before DEPOSIT on day 1
    Transaction buyDay2 =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("FR0010342592"),
            new BigDecimal("1"),
            new Money(new BigDecimal("1100"), EUR),
            new Money(new BigDecimal("1100"), EUR),
            LocalDate.of(2026, 1, 30),
            BigDecimal.ZERO,
            "Imported from Boursobank");
    Transaction depositDay1 =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("3700"), EUR),
            LocalDate.of(2026, 1, 29),
            BigDecimal.ZERO,
            "Imported from Boursobank");
    CsvImportResult parsed = new CsvImportResult(2, 0, 0, List.of(), List.of(buyDay2, depositDay1));

    CsvImportResult result = service.importCsv(account.id(), parsed, ownerId);

    assertThat(result.imported()).isEqualTo(2);
  }

  @Test
  void importCsv_sorts_same_day_deposit_before_buy() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "BoursoBank",
            ownerId,
            null,
            OPENED_AT);
    when(repository.findById(any())).thenReturn(Optional.of(account));

    // Same day: BUY arrives first in parsed list (Boursobank newest-first within same day)
    Transaction sameDayBuy =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("FR0010342592"),
            new BigDecimal("1"),
            new Money(new BigDecimal("1100"), EUR),
            new Money(new BigDecimal("1100"), EUR),
            LocalDate.of(2026, 1, 29),
            BigDecimal.ZERO,
            "Imported from Boursobank");
    Transaction sameDayDeposit =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("3700"), EUR),
            LocalDate.of(2026, 1, 29),
            BigDecimal.ZERO,
            "Imported from Boursobank");
    CsvImportResult parsed =
        new CsvImportResult(2, 0, 0, List.of(), List.of(sameDayBuy, sameDayDeposit));

    CsvImportResult result = service.importCsv(account.id(), parsed, ownerId);

    assertThat(result.imported()).isEqualTo(2);
  }

  @Test
  void updateTransaction_throws_when_edited_deposit_exceeds_livretA_limit() {
    UserId ownerId = UserId.generate();
    FinancialAccount livretA =
        FinancialAccount.open(
            "Livret A",
            AccountType.SAVINGS_ACCOUNT,
            new Money(BigDecimal.ZERO, EUR),
            "BNP",
            ownerId,
            AccountSubType.LIVRET_A,
            OPENED_AT);
    Transaction deposit =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("22000"), EUR),
            LocalDate.of(2026, 1, 1),
            BigDecimal.ZERO,
            null);
    livretA.recordTransaction(deposit);
    when(repository.findById(livretA.id())).thenReturn(Optional.of(livretA));
    when(repository.findAllByOwnerId(ownerId)).thenReturn(List.of(livretA));

    // Edit the deposit to 23 000€ — exceeds 22 950€ Livret A cap
    UpdatedTransactionValues values =
        new UpdatedTransactionValues(
            null,
            null,
            new Money(new BigDecimal("23000"), EUR),
            LocalDate.of(2026, 1, 1),
            BigDecimal.ZERO,
            null);
    UpdateTransactionCommand command =
        new UpdateTransactionCommand(livretA.id(), deposit.id(), ownerId, values);

    assertThatThrownBy(() -> service.updateTransaction(command))
        .isInstanceOf(DepositLimitExceededException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void updateTransaction_allows_editing_deposit_within_livretA_limit() {
    UserId ownerId = UserId.generate();
    FinancialAccount livretA =
        FinancialAccount.open(
            "Livret A",
            AccountType.SAVINGS_ACCOUNT,
            new Money(BigDecimal.ZERO, EUR),
            "BNP",
            ownerId,
            AccountSubType.LIVRET_A,
            OPENED_AT);
    Transaction deposit =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("20000"), EUR),
            LocalDate.of(2026, 1, 1),
            BigDecimal.ZERO,
            null);
    livretA.recordTransaction(deposit);
    when(repository.findById(livretA.id())).thenReturn(Optional.of(livretA));
    when(repository.findAllByOwnerId(ownerId)).thenReturn(List.of(livretA));

    // Edit to 22 000€ — still under 22 950€ cap
    UpdatedTransactionValues values =
        new UpdatedTransactionValues(
            null,
            null,
            new Money(new BigDecimal("22000"), EUR),
            LocalDate.of(2026, 1, 1),
            BigDecimal.ZERO,
            null);
    UpdateTransactionCommand command =
        new UpdateTransactionCommand(livretA.id(), deposit.id(), ownerId, values);

    service.updateTransaction(command);

    verify(repository, times(1)).save(livretA);
  }

  @Test
  void updateTransaction_throws_when_edited_pea_deposit_exceeds_limit() {
    UserId ownerId = UserId.generate();
    FinancialAccount pea =
        FinancialAccount.open(
            "PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            AccountSubType.PEA,
            OPENED_AT);
    Transaction deposit =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("140000"), EUR),
            LocalDate.of(2026, 1, 1),
            BigDecimal.ZERO,
            null);
    pea.recordTransaction(deposit);
    when(repository.findById(pea.id())).thenReturn(Optional.of(pea));
    when(repository.findAllByOwnerId(ownerId)).thenReturn(List.of(pea));

    // Edit to 160 000€ — exceeds 150 000€ PEA cap
    UpdatedTransactionValues values =
        new UpdatedTransactionValues(
            null,
            null,
            new Money(new BigDecimal("160000"), EUR),
            LocalDate.of(2026, 1, 1),
            BigDecimal.ZERO,
            null);
    UpdateTransactionCommand command =
        new UpdateTransactionCommand(pea.id(), deposit.id(), ownerId, values);

    assertThatThrownBy(() -> service.updateTransaction(command))
        .isInstanceOf(DepositLimitExceededException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void updateTransaction_no_limit_check_for_non_deposit_types() {
    UserId ownerId = UserId.generate();
    FinancialAccount pea =
        FinancialAccount.open(
            "PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            AccountSubType.PEA,
            OPENED_AT);
    Transaction deposit =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), EUR),
            LocalDate.of(2026, 1, 1),
            BigDecimal.ZERO,
            null);
    pea.recordTransaction(deposit);
    Transaction buy =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            new BigDecimal("10"),
            new Money(new BigDecimal("100"), EUR),
            new Money(new BigDecimal("1000"), EUR),
            LocalDate.of(2026, 1, 2),
            BigDecimal.ZERO,
            null);
    pea.recordTransaction(buy);
    when(repository.findById(pea.id())).thenReturn(Optional.of(pea));

    // Edit the BUY — type is not DEPOSIT so no deposit limit check runs
    UpdatedTransactionValues values =
        new UpdatedTransactionValues(
            new BigDecimal("5"),
            new Money(new BigDecimal("200"), EUR),
            new Money(new BigDecimal("1000"), EUR),
            LocalDate.of(2026, 1, 2),
            BigDecimal.ZERO,
            null);
    UpdateTransactionCommand command =
        new UpdateTransactionCommand(pea.id(), buy.id(), ownerId, values);

    service.updateTransaction(command);

    verify(repository).save(pea);
    verify(repository, never()).findAllByOwnerId(any());
  }

  @Test
  void getTransactionType_returns_correct_type() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    Transaction deposit =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("5000"), EUR),
            LocalDate.of(2026, 1, 1),
            BigDecimal.ZERO,
            null);
    account.recordTransaction(deposit);
    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    TransactionType result = service.getTransactionType(account.id(), deposit.id(), ownerId);

    assertThat(result).isEqualTo(TransactionType.DEPOSIT);
  }

  @Test
  void getTransactionType_throws_when_ownership_mismatch() {
    FinancialAccount account = openAccount("My PEA", "10000");
    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    assertThatThrownBy(
            () ->
                service.getTransactionType(
                    account.id(), TransactionId.generate(), UserId.generate()))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void getTransactionType_throws_when_transaction_not_found() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    assertThatThrownBy(
            () -> service.getTransactionType(account.id(), TransactionId.generate(), ownerId))
        .isInstanceOf(TransactionNotFoundException.class);
  }

  @Test
  void updateTransaction_delegates_to_domain_and_saves() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    Transaction deposit =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("1000"), EUR),
            LocalDate.of(2026, 1, 1),
            BigDecimal.ZERO,
            null);
    account.recordTransaction(deposit);
    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    UpdatedTransactionValues values =
        new UpdatedTransactionValues(
            null,
            null,
            new Money(new BigDecimal("2000"), EUR),
            LocalDate.of(2026, 1, 1),
            BigDecimal.ZERO,
            "Updated");
    UpdateTransactionCommand command =
        new UpdateTransactionCommand(account.id(), deposit.id(), ownerId, values);

    service.updateTransaction(command);

    verify(repository).save(account);
  }

  @Test
  void updateTransaction_throws_AccountNotFound_on_ownership_mismatch() {
    FinancialAccount account = openAccount("My PEA", "10000");
    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    UpdatedTransactionValues values =
        new UpdatedTransactionValues(
            null,
            null,
            new Money(new BigDecimal("500"), EUR),
            LocalDate.of(2026, 1, 1),
            BigDecimal.ZERO,
            null);
    UpdateTransactionCommand command =
        new UpdateTransactionCommand(
            account.id(), TransactionId.generate(), UserId.generate(), values);

    assertThatThrownBy(() -> service.updateTransaction(command))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void updateTransaction_throws_AccountNotFoundException_when_account_not_found() {
    FinancialAccountId id = FinancialAccountId.generate();
    when(repository.findById(id)).thenReturn(Optional.empty());

    UpdatedTransactionValues values =
        new UpdatedTransactionValues(
            null,
            null,
            new Money(new BigDecimal("500"), EUR),
            LocalDate.of(2026, 1, 1),
            BigDecimal.ZERO,
            null);
    UpdateTransactionCommand command =
        new UpdateTransactionCommand(id, TransactionId.generate(), UserId.generate(), values);

    assertThatThrownBy(() -> service.updateTransaction(command))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void updateTransaction_throws_TransactionNotFoundException_when_tx_not_in_account() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    UpdatedTransactionValues values =
        new UpdatedTransactionValues(
            null,
            null,
            new Money(new BigDecimal("500"), EUR),
            LocalDate.of(2026, 1, 1),
            BigDecimal.ZERO,
            null);
    UpdateTransactionCommand command =
        new UpdateTransactionCommand(account.id(), TransactionId.generate(), ownerId, values);

    assertThatThrownBy(() -> service.updateTransaction(command))
        .isInstanceOf(TransactionNotFoundException.class);
  }

  @Test
  void getEnrichedHoldings_enriches_with_price_when_quote_available() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), EUR),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            new BigDecimal("10"),
            new Money(new BigDecimal("100.00"), EUR),
            new Money(new BigDecimal("1000.00"), EUR),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    when(repository.findById(any())).thenReturn(Optional.of(account));

    Quote quote = new Quote("AAPL", new BigDecimal("150.00"), "EUR", "Apple", Instant.now(), null);
    when(marketDataPort.getQuotes(List.of("AAPL"))).thenReturn(Map.of("AAPL", quote));

    List<EnrichedHolding> result = service.getEnrichedHoldings(account.id(), ownerId, "EUR");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).priceAvailable()).isTrue();
    assertThat(result.get(0).currentPrice()).isEqualByComparingTo("150.00");
    assertThat(result.get(0).marketValue()).isEqualByComparingTo("1500.00");
    assertThat(result.get(0).unrealizedPnl()).isEqualByComparingTo("500.00");
  }

  @Test
  void getEnrichedHoldings_returns_withoutPrice_when_quote_not_found() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), EUR),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("OBSCURE"),
            new BigDecimal("5"),
            new Money(new BigDecimal("200.00"), EUR),
            new Money(new BigDecimal("1000.00"), EUR),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    when(repository.findById(any())).thenReturn(Optional.of(account));
    when(marketDataPort.getQuotes(List.of("OBSCURE"))).thenReturn(Map.of());

    List<EnrichedHolding> result = service.getEnrichedHoldings(account.id(), ownerId, "EUR");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).priceAvailable()).isFalse();
    assertThat(result.get(0).currentPrice()).isNull();
    assertThat(result.get(0).marketValue()).isNull();
  }

  @Test
  void getEnrichedHoldings_returns_empty_when_no_holdings() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    when(repository.findById(any())).thenReturn(Optional.of(account));

    List<EnrichedHolding> result = service.getEnrichedHoldings(account.id(), ownerId, "EUR");

    assertThat(result).isEmpty();
  }

  @Test
  void getPortfolioSummaries_returns_live_values_when_prices_available() {
    UserId ownerId = UserId.generate();
    FinancialAccount pea =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), EUR),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            new BigDecimal("1"),
            new Money(new BigDecimal("150.00"), EUR),
            new Money(new BigDecimal("150.00"), EUR),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    when(repository.findAllByOwnerId(ownerId)).thenReturn(List.of(pea));
    Quote quote = new Quote("AAPL", new BigDecimal("300.00"), "EUR", "Apple", Instant.now(), null);
    when(marketDataPort.getQuotes(List.of("AAPL"))).thenReturn(Map.of("AAPL", quote));

    List<AccountPortfolioSummary> result = service.getPortfolioSummaries(ownerId, "EUR");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).livePortfolioValue()).isEqualByComparingTo("300.00");
    assertThat(result.get(0).costPortfolioValue()).isEqualByComparingTo("150.00");
    assertThat(result.get(0).unrealizedPnl()).isEqualByComparingTo("150.00");
    assertThat(result.get(0).unrealizedPnlPct()).isEqualByComparingTo("100.00");
    assertThat(result.get(0).priceAvailable()).isTrue();
  }

  @Test
  void getPortfolioSummaries_falls_back_to_cost_when_no_price() {
    UserId ownerId = UserId.generate();
    FinancialAccount pea =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), EUR),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("OBSCURE"),
            new BigDecimal("5"),
            new Money(new BigDecimal("200.00"), EUR),
            new Money(new BigDecimal("1000.00"), EUR),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    when(repository.findAllByOwnerId(ownerId)).thenReturn(List.of(pea));
    when(marketDataPort.getQuotes(List.of("OBSCURE"))).thenReturn(Map.of());

    List<AccountPortfolioSummary> result = service.getPortfolioSummaries(ownerId, "EUR");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).livePortfolioValue())
        .isEqualByComparingTo(result.get(0).costPortfolioValue());
    assertThat(result.get(0).priceAvailable()).isFalse();
  }

  @Test
  void getPortfolioSummaries_skips_non_investment_accounts() {
    UserId ownerId = UserId.generate();
    FinancialAccount savings =
        FinancialAccount.open(
            "Livret A",
            AccountType.SAVINGS_ACCOUNT,
            new Money(BigDecimal.ZERO, EUR),
            "BNP",
            ownerId,
            AccountSubType.LIVRET_A,
            OPENED_AT);
    FinancialAccount pea =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    when(repository.findAllByOwnerId(ownerId)).thenReturn(List.of(savings, pea));

    List<AccountPortfolioSummary> result = service.getPortfolioSummaries(ownerId, "EUR");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).accountId()).isEqualTo(pea.id());
  }

  @Test
  void getPortfolioSummaries_returns_zeros_for_account_with_no_holdings() {
    UserId ownerId = UserId.generate();
    FinancialAccount pea =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("5000"), EUR),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    when(repository.findAllByOwnerId(ownerId)).thenReturn(List.of(pea));

    List<AccountPortfolioSummary> result = service.getPortfolioSummaries(ownerId, "EUR");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).livePortfolioValue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.get(0).costPortfolioValue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.get(0).priceAvailable()).isFalse();
  }

  @Test
  void getEnrichedHoldings_converts_eur_cost_to_usd_target() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "Mon CTO",
            AccountType.COMPTE_TITRES,
            new Money(BigDecimal.ZERO, EUR),
            "IBKR",
            ownerId,
            null,
            OPENED_AT);
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), EUR),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            new BigDecimal("10"),
            new Money(new BigDecimal("100.00"), EUR),
            new Money(new BigDecimal("1000.00"), EUR),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    when(repository.findById(any())).thenReturn(Optional.of(account));

    // Quote in USD, target is USD → liveToTarget short-circuits to ONE (no FX call for live)
    Quote quote = new Quote("AAPL", new BigDecimal("100.00"), "USD", "Apple", Instant.now(), null);
    when(marketDataPort.getQuotes(List.of("AAPL"))).thenReturn(Map.of("AAPL", quote));
    // costToTarget: EUR→USD because targetCurrency != "EUR"
    when(fxRatePort.getRate("EUR", "USD")).thenReturn(Optional.of(new BigDecimal("1.10")));

    List<EnrichedHolding> result = service.getEnrichedHoldings(account.id(), ownerId, "USD");

    assertThat(result).hasSize(1);
    // costInTarget = 1000 EUR * 1.10 = 1100 USD, marketValue = 100 USD * 10 = 1000 USD
    assertThat(result.get(0).marketValue()).isEqualByComparingTo("1000.00");
    assertThat(result.get(0).unrealizedPnl()).isEqualByComparingTo("-100.00");
  }

  @Test
  void createAccount_initial_deposit_forces_eur_when_subtype_is_eur_only() {
    // CRYPTO_WALLET is NOT in EUR_ONLY_TYPES, but LIVRET_A IS in EUR_ONLY_SUBTYPES.
    // This exercises the second branch of isEurOnly: subType != null && EUR_ONLY_SUBTYPES.contains
    UserId ownerId = UserId.generate();
    Money initialBalance = new Money(new BigDecimal("1000"), Currency.getInstance("USD"));
    CreateFinancialAccountCommand command =
        new CreateFinancialAccountCommand(
            "Test Account",
            AccountType.CRYPTO_WALLET,
            initialBalance,
            "Test",
            ownerId,
            AccountSubType.LIVRET_A,
            OPENED_AT,
            "USD");

    service.createAccount(command);

    ArgumentCaptor<FinancialAccount> captor = ArgumentCaptor.forClass(FinancialAccount.class);
    verify(repository, times(1)).save(captor.capture());
    Transaction deposit = captor.getAllValues().get(0).transactions().get(0);
    assertThat(deposit.currency()).isEqualTo("EUR");
    assertThat(deposit.eurFxRate()).isEqualByComparingTo(BigDecimal.ONE);
    verify(fxRatePort, never()).getRateToEur(any(), any());
  }

  @Test
  void createAccount_initial_deposit_uses_native_currency_for_crypto() {
    UserId ownerId = UserId.generate();
    Money initialBalance = new Money(new BigDecimal("500"), Currency.getInstance("USD"));
    CreateFinancialAccountCommand command =
        new CreateFinancialAccountCommand(
            "My Crypto",
            AccountType.CRYPTO_WALLET,
            initialBalance,
            "Binance",
            ownerId,
            null,
            OPENED_AT,
            "USD");
    when(fxRatePort.getRateToEur(eq("USD"), any(LocalDate.class)))
        .thenReturn(Optional.of(new BigDecimal("0.920000")));

    service.createAccount(command);

    ArgumentCaptor<FinancialAccount> captor = ArgumentCaptor.forClass(FinancialAccount.class);
    verify(repository, times(1)).save(captor.capture());
    Transaction deposit = captor.getAllValues().get(0).transactions().get(0);
    assertThat(deposit.currency()).isEqualTo("USD");
    assertThat(deposit.eurFxRate()).isEqualByComparingTo(new BigDecimal("0.920000"));
    assertThat(deposit.amountEur()).isEqualByComparingTo(new BigDecimal("460.0000"));
  }

  @Test
  void createAccount_initial_deposit_forces_eur_for_pea() {
    UserId ownerId = UserId.generate();
    // Client sends USD but PEA is EUR-only — service must force EUR
    Money initialBalance = new Money(new BigDecimal("1000"), Currency.getInstance("USD"));
    CreateFinancialAccountCommand command =
        new CreateFinancialAccountCommand(
            "Mon PEA",
            AccountType.PEA,
            initialBalance,
            "Fortuneo",
            ownerId,
            AccountSubType.PEA,
            OPENED_AT,
            "USD");

    service.createAccount(command);

    ArgumentCaptor<FinancialAccount> captor = ArgumentCaptor.forClass(FinancialAccount.class);
    verify(repository, times(1)).save(captor.capture());
    Transaction deposit = captor.getAllValues().get(0).transactions().get(0);
    assertThat(deposit.currency()).isEqualTo("EUR");
    assertThat(deposit.eurFxRate()).isEqualByComparingTo(BigDecimal.ONE);
    verify(fxRatePort, never()).getRateToEur(any(), any());
  }

  @Test
  void getPortfolioSummaries_converts_cost_to_target_currency() {
    UserId ownerId = UserId.generate();
    FinancialAccount pea =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), EUR),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            new BigDecimal("1"),
            new Money(new BigDecimal("100.00"), EUR),
            new Money(new BigDecimal("100.00"), EUR),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    when(repository.findAllByOwnerId(ownerId)).thenReturn(List.of(pea));

    // Quote in USD, target is USD → liveToTarget short-circuits to ONE
    Quote quote = new Quote("AAPL", new BigDecimal("100.00"), "USD", "Apple", Instant.now(), null);
    when(marketDataPort.getQuotes(List.of("AAPL"))).thenReturn(Map.of("AAPL", quote));
    // costToTarget: EUR→USD
    when(fxRatePort.getRate("EUR", "USD")).thenReturn(Optional.of(new BigDecimal("1.10")));

    List<AccountPortfolioSummary> result = service.getPortfolioSummaries(ownerId, "USD");

    assertThat(result).hasSize(1);
    // costPortfolioValue = 100 EUR * 1.10 = 110.00 USD
    assertThat(result.get(0).costPortfolioValue()).isEqualByComparingTo("110.00");
    // livePortfolioValue = 100 USD * 1 = 100.00 USD
    assertThat(result.get(0).livePortfolioValue()).isEqualByComparingTo("100.00");
    assertThat(result.get(0).unrealizedPnl()).isEqualByComparingTo("-10.00");
  }

  @Test
  void getHoldings_merges_multiple_buys_same_ticker_into_one_holding() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), EUR),
            LocalDate.of(2026, 4, 1),
            BigDecimal.ZERO,
            null));
    Transaction buy1 =
        Transaction.ofEur(
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
        Transaction.ofEur(
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

    List<Holding> holdings = service.getHoldings(account.id(), ownerId);

    assertThat(holdings).hasSize(1);
    assertThat(holdings.get(0).ticker().symbol()).isEqualTo("AAPL");
    assertThat(holdings.get(0).quantity()).isEqualByComparingTo(new BigDecimal("10"));
    assertThat(holdings.get(0).averageCostPrice().amount())
        .isEqualByComparingTo(new BigDecimal("150.00"));
  }

  @Test
  void getEnrichedHoldings_converts_usd_price_to_eur_target() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "Mon CTO",
            AccountType.COMPTE_TITRES,
            new Money(BigDecimal.ZERO, EUR),
            "IBKR",
            ownerId,
            null,
            OPENED_AT);
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), EUR),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AAPL"),
            new BigDecimal("10"),
            new Money(new BigDecimal("100.00"), EUR),
            new Money(new BigDecimal("1000.00"), EUR),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    when(repository.findById(any())).thenReturn(Optional.of(account));

    Quote quote = new Quote("AAPL", new BigDecimal("200.00"), "USD", "Apple", Instant.now(), null);
    when(marketDataPort.getQuotes(List.of("AAPL"))).thenReturn(Map.of("AAPL", quote));
    when(fxRatePort.getRate("USD", "EUR")).thenReturn(Optional.of(new BigDecimal("0.90")));

    List<EnrichedHolding> result = service.getEnrichedHoldings(account.id(), ownerId, "EUR");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).priceAvailable()).isTrue();
    assertThat(result.get(0).currency()).isEqualTo("EUR");
    // 200.00 USD * 0.90 = 180.00 EUR
    assertThat(result.get(0).currentPrice()).isEqualByComparingTo("180.00");
    // 180.00 * 10 shares = 1800.00
    assertThat(result.get(0).marketValue()).isEqualByComparingTo("1800.00");
  }

  @Test
  void getEnrichedHoldings_uses_fx_rate_1_when_currencies_match() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("5000"), EUR),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            new Ticker("AIR.PA"),
            new BigDecimal("5"),
            new Money(new BigDecimal("150.00"), EUR),
            new Money(new BigDecimal("750.00"), EUR),
            LocalDate.now(),
            BigDecimal.ZERO,
            null));
    when(repository.findById(any())).thenReturn(Optional.of(account));

    Quote quote =
        new Quote("AIR.PA", new BigDecimal("160.00"), "EUR", "Airbus", Instant.now(), null);
    when(marketDataPort.getQuotes(List.of("AIR.PA"))).thenReturn(Map.of("AIR.PA", quote));

    List<EnrichedHolding> result = service.getEnrichedHoldings(account.id(), ownerId, "EUR");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).currentPrice()).isEqualByComparingTo("160.00");
    assertThat(result.get(0).marketValue()).isEqualByComparingTo("800.00");
  }

  @Test
  void recordTransaction_stores_rate_1_for_eur_transaction() {
    FinancialAccount account = openAccount("My PEA", "10000");
    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    RecordTransactionCommand command =
        new RecordTransactionCommand(
            account.id(),
            account.ownerId(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("500"), EUR),
            LocalDate.of(2026, 6, 1),
            BigDecimal.ZERO,
            null,
            "EUR");

    service.recordTransaction(command);

    Transaction recorded = account.transactions().get(account.transactions().size() - 1);
    assertThat(recorded.currency()).isEqualTo("EUR");
    assertThat(recorded.eurFxRate()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(recorded.amountEur()).isEqualByComparingTo(new BigDecimal("500"));
  }

  @Test
  void recordTransaction_stores_eur_fx_rate_for_usd_transaction() {
    // CRYPTO_WALLET is not EUR-only — USD transactions are permitted
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "My Crypto",
            AccountType.CRYPTO_WALLET,
            new Money(new BigDecimal("10000"), EUR),
            "Binance",
            ownerId,
            null,
            OPENED_AT);
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), EUR),
            OPENED_AT,
            BigDecimal.ZERO,
            null));
    when(repository.findById(account.id())).thenReturn(Optional.of(account));
    LocalDate txDate = LocalDate.of(2026, 6, 1);
    when(fxRatePort.getRateToEur("USD", txDate))
        .thenReturn(Optional.of(new BigDecimal("0.920000")));

    RecordTransactionCommand command =
        new RecordTransactionCommand(
            account.id(),
            ownerId,
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("1000"), EUR),
            txDate,
            BigDecimal.ZERO,
            null,
            "USD");

    service.recordTransaction(command);

    Transaction recorded = account.transactions().get(account.transactions().size() - 1);
    assertThat(recorded.currency()).isEqualTo("USD");
    assertThat(recorded.eurFxRate()).isEqualByComparingTo(new BigDecimal("0.920000"));
    assertThat(recorded.amountEur()).isEqualByComparingTo(new BigDecimal("920.0000"));
  }

  @Test
  void recordTransaction_uses_amount_eur_for_deposit_limit_check() {
    UserId ownerId = UserId.generate();
    FinancialAccount livretA =
        FinancialAccount.open(
            "Livret A",
            AccountType.SAVINGS_ACCOUNT,
            new Money(BigDecimal.ZERO, EUR),
            "BNP",
            ownerId,
            AccountSubType.LIVRET_A,
            OPENED_AT);
    Transaction existingDeposit =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("22000"), EUR),
            LocalDate.of(2026, 1, 1),
            BigDecimal.ZERO,
            null);
    livretA.recordTransaction(existingDeposit);
    when(repository.findById(livretA.id())).thenReturn(Optional.of(livretA));
    when(repository.findAllByOwnerId(ownerId)).thenReturn(List.of(livretA));

    // SAVINGS_ACCOUNT is EUR-only: currency forced to EUR; 22000 + 1100 = 23100 > 22950 cap
    RecordTransactionCommand command =
        new RecordTransactionCommand(
            livretA.id(),
            ownerId,
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("1100"), EUR),
            LocalDate.of(2026, 6, 1),
            BigDecimal.ZERO,
            null,
            "EUR");

    assertThatThrownBy(() -> service.recordTransaction(command))
        .isInstanceOf(DepositLimitExceededException.class);
  }

  @Test
  void recordTransaction_eur_only_account_ignores_client_currency_and_forces_eur() {
    FinancialAccount account = openAccount("My PEA", "10000");
    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    RecordTransactionCommand command =
        new RecordTransactionCommand(
            account.id(),
            account.ownerId(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("500"), EUR),
            LocalDate.of(2026, 6, 1),
            BigDecimal.ZERO,
            null,
            "USD"); // client sends USD, but PEA is EUR-only

    service.recordTransaction(command);

    Transaction recorded = account.transactions().get(account.transactions().size() - 1);
    assertThat(recorded.currency()).isEqualTo("EUR");
    assertThat(recorded.eurFxRate()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(recorded.amountEur()).isEqualByComparingTo(new BigDecimal("500"));
  }

  @Test
  void recordTransaction_usd_transaction_stored_with_eur_amount_in_domain() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "My Crypto",
            AccountType.CRYPTO_WALLET,
            new Money(BigDecimal.ZERO, EUR),
            "Binance",
            ownerId,
            null,
            OPENED_AT);
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), EUR),
            OPENED_AT,
            BigDecimal.ZERO,
            null));
    when(repository.findById(account.id())).thenReturn(Optional.of(account));
    LocalDate txDate = LocalDate.of(2026, 6, 1);
    when(fxRatePort.getRateToEur("USD", txDate)).thenReturn(Optional.of(new BigDecimal("0.92")));

    RecordTransactionCommand command =
        new RecordTransactionCommand(
            account.id(),
            ownerId,
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("1000"), EUR),
            txDate,
            BigDecimal.ZERO,
            null,
            "USD");

    service.recordTransaction(command);

    Transaction recorded = account.transactions().get(account.transactions().size() - 1);
    // totalAmount in domain must always be EUR — currency mismatch with EUR balance would throw
    assertThat(recorded.totalAmount().amount()).isEqualByComparingTo(new BigDecimal("920.0000"));
    assertThat(recorded.totalAmount().currency()).isEqualTo(EUR);
    assertThat(recorded.currency()).isEqualTo("USD");
  }

  // --- PEA ≥5y WITHDRAWAL fiscal split tests ---

  private static final LocalDate OVER_5Y_AGO = LocalDate.of(2019, 1, 1);

  private FinancialAccount openPeaOver5y() {
    UserId ownerId = UserId.generate();
    return FinancialAccount.open(
        "Mon PEA",
        AccountType.PEA,
        new Money(BigDecimal.ZERO, EUR),
        "Fortuneo",
        ownerId,
        AccountSubType.PEA,
        OVER_5Y_AGO);
  }

  @Test
  void recordTransaction_pea_over5y_withdrawal_applies_ps_tax() {
    // totalDeposits=100000, dividend=6000 → balance=106000, no holdings → liquidationValue=106000
    // gainGlobal=6000, gainRatio=6000/106000=0.056604
    // withdrawal=10000, taxableGain=566.04, psTax=105.28, netAmount=9894.72
    FinancialAccount account = openPeaOver5y();
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("100000"), EUR),
            OVER_5Y_AGO,
            BigDecimal.ZERO,
            null));
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DIVIDEND,
            null,
            null,
            null,
            new Money(new BigDecimal("6000"), EUR),
            OVER_5Y_AGO,
            BigDecimal.ZERO,
            null));
    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    RecordTransactionCommand command =
        new RecordTransactionCommand(
            account.id(),
            account.ownerId(),
            TransactionType.WITHDRAWAL,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), EUR),
            LocalDate.of(2026, 6, 12),
            BigDecimal.ZERO,
            null,
            "EUR");

    service.recordTransaction(command);

    List<Transaction> withdrawals =
        account.transactions().stream()
            .filter(t -> t.type() == TransactionType.WITHDRAWAL)
            .toList();
    assertThat(withdrawals).hasSize(2);
    BigDecimal totalWithdrawn =
        withdrawals.stream()
            .map(t -> t.totalAmount().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(totalWithdrawn).isEqualByComparingTo(new BigDecimal("10000.00"));
    assertThat(account.balance().amount()).isEqualByComparingTo(new BigDecimal("96000.00"));
    verify(repository).save(account);
  }

  @Test
  void recordTransaction_pea_over5y_withdrawal_no_tax_when_at_loss() {
    // totalDeposits=100000, withdrawal=20000 pre-existing → balance=80000 < totalDeposits → atLoss
    // gainGlobal=max(-20000,0)=0 → psTax=0 → only 1 new WITHDRAWAL (no tax split)
    FinancialAccount account = openPeaOver5y();
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("100000"), EUR),
            OVER_5Y_AGO,
            BigDecimal.ZERO,
            null));
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.WITHDRAWAL,
            null,
            null,
            null,
            new Money(new BigDecimal("20000"), EUR),
            OVER_5Y_AGO,
            BigDecimal.ZERO,
            null));
    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    RecordTransactionCommand command =
        new RecordTransactionCommand(
            account.id(),
            account.ownerId(),
            TransactionType.WITHDRAWAL,
            null,
            null,
            null,
            new Money(new BigDecimal("5000"), EUR),
            LocalDate.of(2026, 6, 12),
            BigDecimal.ZERO,
            null,
            "EUR");

    service.recordTransaction(command);

    // Pre-existing manual withdrawal + 1 from service (no tax split because atLoss)
    long withdrawalCount =
        account.transactions().stream().filter(t -> t.type() == TransactionType.WITHDRAWAL).count();
    assertThat(withdrawalCount).isEqualTo(2L);
    assertThat(account.balance().amount()).isEqualByComparingTo(new BigDecimal("75000.00"));
  }

  @Test
  void recordTransaction_pea_under5y_withdrawal_no_automatic_tax() {
    // PEA <5y → isPeaOlderThan5Years=false → normal WITHDRAWAL path, no fiscal split
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            AccountSubType.PEA,
            OPENED_AT); // 2024-01-01 — under 5 years
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), EUR),
            OPENED_AT,
            BigDecimal.ZERO,
            null));
    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    RecordTransactionCommand command =
        new RecordTransactionCommand(
            account.id(),
            ownerId,
            TransactionType.WITHDRAWAL,
            null,
            null,
            null,
            new Money(new BigDecimal("3000"), EUR),
            LocalDate.of(2026, 6, 12),
            BigDecimal.ZERO,
            null,
            "EUR");

    service.recordTransaction(command);

    // Exactly 1 WITHDRAWAL recorded (full amount, no PS tax split)
    long withdrawalCount =
        account.transactions().stream().filter(t -> t.type() == TransactionType.WITHDRAWAL).count();
    assertThat(withdrawalCount).isEqualTo(1L);
    assertThat(account.transactions().get(account.transactions().size() - 1).totalAmount().amount())
        .isEqualByComparingTo(new BigDecimal("3000"));
  }

  @Test
  void createAccount_throws_when_initial_deposit_exceeds_livret_a_cap() {
    UserId ownerId = UserId.generate();
    when(repository.findAllByOwnerId(ownerId)).thenReturn(List.of());

    // 25 000 EUR initial deposit exceeds Livret A cap of 22 950 EUR
    CreateFinancialAccountCommand command =
        new CreateFinancialAccountCommand(
            "Mon Livret A",
            AccountType.SAVINGS_ACCOUNT,
            new Money(new BigDecimal("25000"), EUR),
            "BNP",
            ownerId,
            AccountSubType.LIVRET_A,
            OPENED_AT,
            "EUR");

    assertThatThrownBy(() -> service.createAccount(command))
        .isInstanceOf(DepositLimitExceededException.class);
  }

  @Test
  void createAccount_throws_when_duplicate_livret_a() {
    UserId ownerId = UserId.generate();
    FinancialAccount existing =
        FinancialAccount.open(
            "Livret A",
            AccountType.SAVINGS_ACCOUNT,
            new Money(BigDecimal.ZERO, EUR),
            "BNP",
            ownerId,
            AccountSubType.LIVRET_A,
            OPENED_AT);
    when(repository.findAllByOwnerId(ownerId)).thenReturn(List.of(existing));

    CreateFinancialAccountCommand command =
        new CreateFinancialAccountCommand(
            "Deuxième Livret A",
            AccountType.SAVINGS_ACCOUNT,
            new Money(BigDecimal.ZERO, EUR),
            "BNP",
            ownerId,
            AccountSubType.LIVRET_A,
            OPENED_AT,
            "EUR");

    assertThatThrownBy(() -> service.createAccount(command))
        .isInstanceOf(AccountCardinalityException.class)
        .hasMessageContaining("LIVRET_A");
  }

  @Test
  void createAccount_allows_pea_and_pea_pme() {
    UserId ownerId = UserId.generate();
    FinancialAccount existingPea =
        FinancialAccount.open(
            "PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            AccountSubType.PEA,
            OPENED_AT);
    when(repository.findAllByOwnerId(ownerId)).thenReturn(List.of(existingPea));

    CreateFinancialAccountCommand command =
        new CreateFinancialAccountCommand(
            "PEA-PME",
            AccountType.PEA_PME,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            AccountSubType.PEA_PME,
            OPENED_AT,
            "EUR");

    FinancialAccountId result = service.createAccount(command);

    assertThat(result).isNotNull();
  }

  @Test
  void createAccount_throws_when_second_pea() {
    UserId ownerId = UserId.generate();
    FinancialAccount existingPea =
        FinancialAccount.open(
            "PEA existant",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            AccountSubType.PEA,
            OPENED_AT);
    when(repository.findAllByOwnerId(ownerId)).thenReturn(List.of(existingPea));

    CreateFinancialAccountCommand command =
        new CreateFinancialAccountCommand(
            "Second PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            AccountSubType.PEA,
            OPENED_AT,
            "EUR");

    assertThatThrownBy(() -> service.createAccount(command))
        .isInstanceOf(AccountCardinalityException.class)
        .hasMessageContaining("PEA");
  }

  @Test
  void deleteTransaction_delegates_to_domain_and_saves() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "My PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            null,
            OPENED_AT);
    Transaction deposit =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("1000"), EUR),
            OPENED_AT,
            BigDecimal.ZERO,
            null);
    account.recordTransaction(deposit);
    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    service.deleteTransaction(account.id(), deposit.id(), ownerId);

    assertThat(account.transactions()).isEmpty();
    verify(repository).save(account);
  }

  @Test
  void deleteTransaction_throws_when_account_not_owned_by_user() {
    FinancialAccount account = openAccount("My PEA", "1000");
    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    assertThatThrownBy(
            () ->
                service.deleteTransaction(
                    account.id(), TransactionId.generate(), UserId.generate()))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void recordTransaction_eur_fees_correctly_converted() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "My Crypto",
            AccountType.CRYPTO_WALLET,
            new Money(BigDecimal.ZERO, EUR),
            "Binance",
            ownerId,
            null,
            OPENED_AT);
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("10000"), EUR),
            OPENED_AT,
            BigDecimal.ZERO,
            null));
    when(repository.findById(account.id())).thenReturn(Optional.of(account));
    LocalDate txDate = LocalDate.of(2026, 6, 1);
    when(fxRatePort.getRateToEur("USD", txDate)).thenReturn(Optional.of(new BigDecimal("0.92")));

    RecordTransactionCommand command =
        new RecordTransactionCommand(
            account.id(),
            ownerId,
            TransactionType.BUY,
            new Ticker("BTC-USD"),
            new BigDecimal("0.1"),
            new Money(new BigDecimal("50000"), EUR),
            new Money(new BigDecimal("5000"), EUR),
            txDate,
            new BigDecimal("10"),
            null,
            "USD");

    service.recordTransaction(command);

    Transaction recorded = account.transactions().get(account.transactions().size() - 1);
    // fees must be stored in EUR: 10 USD * 0.92 = 9.20 EUR
    assertThat(recorded.fees()).isEqualByComparingTo(new BigDecimal("9.20"));
  }
}
