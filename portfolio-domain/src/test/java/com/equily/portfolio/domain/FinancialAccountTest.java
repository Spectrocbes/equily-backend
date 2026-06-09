package com.equily.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.exception.InsufficientFundsException;
import com.equily.portfolio.domain.exception.InvalidFinancialAccountException;
import com.equily.portfolio.domain.exception.InvalidHoldingException;
import com.equily.portfolio.domain.exception.TransactionNotFoundException;
import com.equily.shared.Country;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FinancialAccountTest {

  private static final Currency EUR = Currency.getInstance("EUR");
  private static final LocalDate TODAY = LocalDate.of(2026, 5, 22);
  private static final Ticker AAPL = new Ticker("AAPL");
  private static final AssetMetadata AAPL_META =
      new AssetMetadata("Apple Inc.", "US0378331005", new Country("US"));

  private FinancialAccount accountWith(String balance) {
    FinancialAccount account =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            UserId.generate(),
            null,
            TODAY);
    BigDecimal amount = new BigDecimal(balance);
    if (amount.compareTo(BigDecimal.ZERO) > 0) {
      account.recordTransaction(deposit(balance));
    }
    return account;
  }

  private Transaction deposit(String amount) {
    return Transaction.ofEur(
        TransactionId.generate(),
        TransactionType.DEPOSIT,
        null,
        null,
        null,
        new Money(new BigDecimal(amount), EUR),
        TODAY,
        null,
        null);
  }

  private Transaction withdrawal(String amount) {
    return Transaction.ofEur(
        TransactionId.generate(),
        TransactionType.WITHDRAWAL,
        null,
        null,
        null,
        new Money(new BigDecimal(amount), EUR),
        TODAY,
        null,
        null);
  }

  private Transaction buy(String qty, String price) {
    BigDecimal q = new BigDecimal(qty);
    BigDecimal p = new BigDecimal(price);
    return Transaction.ofEur(
        TransactionId.generate(),
        TransactionType.BUY,
        AAPL,
        q,
        new Money(p, EUR),
        new Money(q.multiply(p), EUR),
        TODAY,
        null,
        null);
  }

  private Transaction sell(String qty, String price) {
    BigDecimal q = new BigDecimal(qty);
    BigDecimal p = new BigDecimal(price);
    return Transaction.ofEur(
        TransactionId.generate(),
        TransactionType.SELL,
        AAPL,
        q,
        new Money(p, EUR),
        new Money(q.multiply(p), EUR),
        TODAY,
        null,
        null);
  }

  @Test
  void open_preserves_broker() {
    FinancialAccount account =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            UserId.generate(),
            null,
            TODAY);
    assertThat(account.broker()).isEqualTo("Fortuneo");
  }

  @Test
  void open_preserves_ownerId() {
    UserId ownerId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            ownerId,
            null,
            TODAY);
    assertThat(account.ownerId()).isEqualTo(ownerId);
  }

  @Test
  void open_preserves_openedAt() {
    LocalDate openedAt = LocalDate.of(2020, 1, 15);
    FinancialAccount account =
        FinancialAccount.open(
            "Mon PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            UserId.generate(),
            null,
            openedAt);
    assertThat(account.openedAt()).isEqualTo(openedAt);
  }

  @Test
  void open_with_null_ownerId_throws() {
    assertThatThrownBy(
            () ->
                FinancialAccount.open(
                    "Mon PEA",
                    AccountType.PEA,
                    new Money(BigDecimal.ZERO, EUR),
                    "Fortuneo",
                    null,
                    null,
                    TODAY))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void open_with_null_openedAt_throws() {
    assertThatThrownBy(
            () ->
                FinancialAccount.open(
                    "Mon PEA",
                    AccountType.PEA,
                    new Money(BigDecimal.ZERO, EUR),
                    "Fortuneo",
                    UserId.generate(),
                    null,
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void open_with_null_broker_throws() {
    assertThatThrownBy(
            () ->
                FinancialAccount.open(
                    "Mon PEA",
                    AccountType.PEA,
                    new Money(BigDecimal.ZERO, EUR),
                    null,
                    UserId.generate(),
                    null,
                    TODAY))
        .isInstanceOf(InvalidFinancialAccountException.class);
  }

  @Test
  void open_with_blank_broker_throws() {
    assertThatThrownBy(
            () ->
                FinancialAccount.open(
                    "Mon PEA",
                    AccountType.PEA,
                    new Money(BigDecimal.ZERO, EUR),
                    "  ",
                    UserId.generate(),
                    null,
                    TODAY))
        .isInstanceOf(InvalidFinancialAccountException.class);
  }

  @Test
  void open_with_blank_name_throws() {
    assertThatThrownBy(
            () ->
                FinancialAccount.open(
                    "  ",
                    AccountType.PEA,
                    new Money(BigDecimal.ZERO, EUR),
                    null,
                    UserId.generate(),
                    null,
                    TODAY))
        .isInstanceOf(InvalidFinancialAccountException.class);
  }

  @Test
  void open_with_null_accountType_throws() {
    assertThatThrownBy(
            () ->
                FinancialAccount.open(
                    "Mon PEA",
                    null,
                    new Money(BigDecimal.ZERO, EUR),
                    null,
                    UserId.generate(),
                    null,
                    TODAY))
        .isInstanceOf(InvalidFinancialAccountException.class);
  }

  @Test
  void open_with_null_initialBalance_throws() {
    assertThatThrownBy(
            () ->
                FinancialAccount.open(
                    "Mon PEA", AccountType.PEA, null, null, UserId.generate(), null, TODAY))
        .isInstanceOf(InvalidFinancialAccountException.class);
  }

  @Test
  void deposit_increases_balance() {
    FinancialAccount account = accountWith("0");
    account.recordTransaction(deposit("1000.00"));
    assertThat(account.balance()).isEqualTo(new Money(new BigDecimal("1000.00"), EUR));
  }

  @Test
  void withdrawal_decreases_balance() {
    FinancialAccount account = accountWith("500.00");
    account.recordTransaction(withdrawal("200.00"));
    assertThat(account.balance()).isEqualTo(new Money(new BigDecimal("300.00"), EUR));
  }

  @Test
  void withdrawal_exceeding_balance_throws_InsufficientFundsException() {
    FinancialAccount account = accountWith("100.00");
    assertThatThrownBy(() -> account.recordTransaction(withdrawal("200.00")))
        .isInstanceOf(InsufficientFundsException.class);
  }

  @Test
  void buy_decreases_balance_and_holding_appears() {
    FinancialAccount account = accountWith("2000.00");
    account.recordTransaction(buy("10", "150.00"));

    assertThat(account.balance()).isEqualTo(new Money(new BigDecimal("500.00"), EUR));

    Map<Ticker, FinancialAccount.AssetInfo> info =
        Map.of(AAPL, new FinancialAccount.AssetInfo(AssetType.STOCK, AAPL_META));
    List<Holding> holdings = account.getHoldings(info);

    assertThat(holdings).hasSize(1);
    assertThat(holdings.get(0).ticker()).isEqualTo(AAPL);
    assertThat(holdings.get(0).quantity()).isEqualByComparingTo(new BigDecimal("10"));
  }

  @Test
  void insufficientFundsException_message_format() {
    Money available = new Money(new BigDecimal("100.00"), EUR);
    Money attempted = new Money(new BigDecimal("200.00"), EUR);
    InsufficientFundsException ex = new InsufficientFundsException(attempted, available);
    assertThat(ex.getMessage())
        .contains("Insufficient funds")
        .contains("available")
        .contains("required");
  }

  @Test
  void invalidHoldingException_message_format() {
    InvalidHoldingException ex =
        new InvalidHoldingException("AAPL", new BigDecimal("6"), new BigDecimal("5"));
    assertThat(ex.getMessage()).isEqualTo("Cannot sell 6 AAPL — you only hold 5");
  }

  @Test
  void invalidHoldingException_single_arg_constructor_preserves_message() {
    InvalidHoldingException ex = new InvalidHoldingException("custom error message");
    assertThat(ex.getMessage()).isEqualTo("custom error message");
  }

  @Test
  void sell_exceeding_quantity_throws_InvalidHoldingException() {
    FinancialAccount account = accountWith("2000.00");
    account.recordTransaction(buy("5", "100.00"));

    assertThatThrownBy(() -> account.recordTransaction(sell("6", "110.00")))
        .isInstanceOf(InvalidHoldingException.class)
        .hasMessageContaining("AAPL");
  }

  @Test
  void sell_exceeding_quantity_is_not_persisted_to_transaction_list() {
    FinancialAccount account = accountWith("2000.00");
    account.recordTransaction(buy("5", "100.00"));

    try {
      account.recordTransaction(sell("6", "110.00"));
    } catch (InvalidHoldingException ignored) {
    }

    assertThat(account.transactions()).hasSize(2); // deposit (from helper) + 1 successful buy
  }

  @Test
  void recordTransaction_INTEREST_increases_balance() {
    FinancialAccount account = accountWith("500.00");
    Transaction interest =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.INTEREST,
            null,
            null,
            null,
            new Money(new BigDecimal("10.00"), EUR),
            TODAY,
            BigDecimal.ZERO,
            "Monthly interest");
    account.recordTransaction(interest);
    assertThat(account.balance()).isEqualTo(new Money(new BigDecimal("510.00"), EUR));
  }

  @Test
  void recordTransaction_INTEREST_does_not_affect_deposit_limit() {
    FinancialAccount account = accountWith("1000.00");
    Transaction interest =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.INTEREST,
            null,
            null,
            null,
            new Money(new BigDecimal("50.00"), EUR),
            TODAY,
            BigDecimal.ZERO,
            null);
    account.recordTransaction(interest);

    BigDecimal depositTotal =
        account.transactions().stream()
            .filter(t -> t.type() == TransactionType.DEPOSIT)
            .map(t -> t.totalAmount().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    // only the 1000 from accountWith counts — INTEREST does not count as a deposit
    assertThat(depositTotal).isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat(account.balance().amount()).isEqualByComparingTo(new BigDecimal("1050.00"));
  }

  @Test
  void updateTransaction_updates_amount_and_recalculates_balance() {
    FinancialAccount account = accountWith("1000.00");
    Transaction second = deposit("500.00");
    account.recordTransaction(second);
    // balance = 1000 + 500 = 1500

    UpdatedTransactionValues values =
        new UpdatedTransactionValues(
            null, null, new Money(new BigDecimal("200.00"), EUR), TODAY, BigDecimal.ZERO, null);
    account.updateTransaction(second.id(), values);

    // balance recomputed: 1000 + 200 = 1200
    assertThat(account.balance()).isEqualTo(new Money(new BigDecimal("1200.00"), EUR));
    assertThat(account.transactions()).hasSize(2);
  }

  @Test
  void updateTransaction_throws_when_transaction_not_found() {
    FinancialAccount account = accountWith("1000.00");
    UpdatedTransactionValues values =
        new UpdatedTransactionValues(
            null, null, new Money(new BigDecimal("500.00"), EUR), TODAY, BigDecimal.ZERO, null);

    assertThatThrownBy(() -> account.updateTransaction(TransactionId.generate(), values))
        .isInstanceOf(TransactionNotFoundException.class);
  }

  @Test
  void updateTransaction_throws_when_edit_creates_negative_balance() {
    FinancialAccount account = accountWith("1000.00");
    Transaction second = deposit("500.00");
    account.recordTransaction(second);
    account.recordTransaction(withdrawal("1200.00")); // balance = 1000 + 500 - 1200 = 300

    // reducing second deposit to 100 → replay: 1000 + 100 - 1200 = -100 → throws
    UpdatedTransactionValues values =
        new UpdatedTransactionValues(
            null, null, new Money(new BigDecimal("100.00"), EUR), TODAY, BigDecimal.ZERO, null);

    assertThatThrownBy(() -> account.updateTransaction(second.id(), values))
        .isInstanceOf(InsufficientFundsException.class);
  }

  @Test
  void sell_increases_balance_and_reduces_holding_quantity() {
    FinancialAccount account = accountWith("2000.00");
    account.recordTransaction(buy("10", "150.00"));
    account.recordTransaction(sell("4", "180.00"));

    // balance: 2000 - 1500 + 720 = 1220
    assertThat(account.balance()).isEqualTo(new Money(new BigDecimal("1220.00"), EUR));

    Map<Ticker, FinancialAccount.AssetInfo> info =
        Map.of(AAPL, new FinancialAccount.AssetInfo(AssetType.STOCK, AAPL_META));
    List<Holding> holdings = account.getHoldings(info);

    assertThat(holdings).hasSize(1);
    assertThat(holdings.get(0).quantity()).isEqualByComparingTo(new BigDecimal("6"));
    assertThat(holdings.get(0).averageCostPrice().amount())
        .isEqualByComparingTo(new BigDecimal("150.00"));
  }
}
