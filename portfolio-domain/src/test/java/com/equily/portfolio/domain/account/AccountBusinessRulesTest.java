package com.equily.portfolio.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.AccountType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.exception.DepositLimitExceededException;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AccountBusinessRulesTest {

  private static final Currency EUR = Currency.getInstance("EUR");
  private static final LocalDate TODAY = LocalDate.of(2026, 5, 30);

  private FinancialAccount openAccount(AccountSubType subType) {
    return FinancialAccount.open(
        "Test Account",
        AccountType.SAVINGS_ACCOUNT,
        new Money(BigDecimal.ZERO, EUR),
        "Test Bank",
        UserId.generate(),
        subType,
        TODAY);
  }

  private FinancialAccount openPeaAccount(AccountSubType subType) {
    return FinancialAccount.open(
        "PEA Account",
        AccountType.PEA,
        new Money(new BigDecimal("5000"), EUR),
        "Fortuneo",
        UserId.generate(),
        subType,
        TODAY);
  }

  private Transaction deposit(String amount) {
    return Transaction.of(
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
    return Transaction.of(
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

  private Transaction dividend(String amount) {
    return Transaction.of(
        TransactionId.generate(),
        TransactionType.DIVIDEND,
        null,
        null,
        null,
        new Money(new BigDecimal(amount), EUR),
        TODAY,
        null,
        null);
  }

  @Test
  void validateDeposit_noOp_when_subType_is_null() {
    FinancialAccount account =
        FinancialAccount.open(
            "CTO",
            AccountType.COMPTE_TITRES,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            UserId.generate(),
            null,
            TODAY);
    // Must not throw
    AccountBusinessRules.validateDeposit(
        account, new Money(new BigDecimal("999999"), EUR), List.of(account));
  }

  @Test
  void validateDeposit_noOp_when_no_limit_for_subType() {
    FinancialAccount account = openAccount(AccountSubType.COMPTE_TITRES);
    // COMPTE_TITRES has no regulatory limit — must not throw
    AccountBusinessRules.validateDeposit(
        account, new Money(new BigDecimal("999999"), EUR), List.of(account));
  }

  @Test
  void validateDeposit_throws_when_livretA_limit_exceeded() {
    FinancialAccount account = openAccount(AccountSubType.LIVRET_A);
    account.recordTransaction(deposit("22000"));

    assertThatThrownBy(
            () ->
                AccountBusinessRules.validateDeposit(
                    account, new Money(new BigDecimal("1000"), EUR), List.of(account)))
        .isInstanceOf(DepositLimitExceededException.class)
        .hasMessageContaining("LIVRET_A");
  }

  @Test
  void validateDeposit_allows_deposit_exactly_at_limit() {
    FinancialAccount account = openAccount(AccountSubType.LIVRET_A);
    account.recordTransaction(deposit("21950"));

    // 21950 + 1000 = 22950 == limit → must not throw
    AccountBusinessRules.validateDeposit(
        account, new Money(new BigDecimal("1000"), EUR), List.of(account));
  }

  @Test
  void validateDeposit_throws_when_pea_limit_exceeded() {
    FinancialAccount account = openPeaAccount(AccountSubType.PEA);
    account.recordTransaction(deposit("145000"));

    assertThatThrownBy(
            () ->
                AccountBusinessRules.validateDeposit(
                    account, new Money(new BigDecimal("10000"), EUR), List.of(account)))
        .isInstanceOf(DepositLimitExceededException.class)
        .hasMessageContaining("PEA");
  }

  @Test
  void validateDeposit_combined_pea_pme_throws_when_combined_exceeds_225k() {
    UserId userId = UserId.generate();
    FinancialAccount peaAccount =
        FinancialAccount.open(
            "PEA",
            AccountType.PEA,
            new Money(new BigDecimal("5000"), EUR),
            "Fortuneo",
            userId,
            AccountSubType.PEA,
            TODAY);
    peaAccount.recordTransaction(deposit("150000"));

    FinancialAccount peaPmeAccount =
        FinancialAccount.open(
            "PEA-PME",
            AccountType.PEA_PME,
            new Money(new BigDecimal("5000"), EUR),
            "Fortuneo",
            userId,
            AccountSubType.PEA_PME,
            TODAY);
    peaPmeAccount.recordTransaction(deposit("70000"));

    List<FinancialAccount> allAccounts = List.of(peaAccount, peaPmeAccount);

    // Combined = 150000 + 70000 = 220000. Trying to add 10000 → 230000 > 225000 → throws
    assertThatThrownBy(
            () ->
                AccountBusinessRules.validateDeposit(
                    peaPmeAccount, new Money(new BigDecimal("10000"), EUR), allAccounts))
        .isInstanceOf(DepositLimitExceededException.class)
        .hasMessageContaining("PEA_PME");
  }

  @Test
  void remainingCapacity_returns_correct_remaining_for_livretA() {
    FinancialAccount account = openAccount(AccountSubType.LIVRET_A);
    account.recordTransaction(deposit("10000"));

    Optional<Money> remaining = AccountBusinessRules.remainingCapacity(account, List.of(account));

    assertThat(remaining).isPresent();
    // 22950 - 10000 = 12950
    assertThat(remaining.get().amount()).isEqualByComparingTo(new BigDecimal("12950"));
  }

  @Test
  void remainingCapacity_returns_empty_when_no_limit_applies() {
    FinancialAccount account = openAccount(AccountSubType.OTHER);

    Optional<Money> remaining = AccountBusinessRules.remainingCapacity(account, List.of(account));

    assertThat(remaining).isEmpty();
  }

  @Test
  void remainingCapacity_returns_empty_when_subType_is_null() {
    FinancialAccount account =
        FinancialAccount.open(
            "CTO",
            AccountType.COMPTE_TITRES,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            UserId.generate(),
            null,
            TODAY);

    Optional<Money> remaining = AccountBusinessRules.remainingCapacity(account, List.of(account));

    assertThat(remaining).isEmpty();
  }

  @Test
  void isApproachingLimit_returns_true_when_over_90_percent() {
    FinancialAccount account = openAccount(AccountSubType.LIVRET_A);
    // 90% of 22950 = 20655 — deposit 21000 (> 20655)
    account.recordTransaction(deposit("21000"));

    boolean approaching = AccountBusinessRules.isApproachingLimit(account, List.of(account));

    assertThat(approaching).isTrue();
  }

  @Test
  void isApproachingLimit_returns_false_when_under_90_percent() {
    FinancialAccount account = openAccount(AccountSubType.LIVRET_A);
    // 90% of 22950 = 20655 — deposit 10000 (< 20655)
    account.recordTransaction(deposit("10000"));

    boolean approaching = AccountBusinessRules.isApproachingLimit(account, List.of(account));

    assertThat(approaching).isFalse();
  }

  @Test
  void isApproachingLimit_returns_false_when_subType_is_null() {
    FinancialAccount account =
        FinancialAccount.open(
            "CTO",
            AccountType.COMPTE_TITRES,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            UserId.generate(),
            null,
            TODAY);

    boolean approaching = AccountBusinessRules.isApproachingLimit(account, List.of(account));

    assertThat(approaching).isFalse();
  }

  @Test
  void validateDeposit_livretA_withdrawal_frees_up_capacity() {
    // Deposit 22950 (fills limit), then withdraw 1000 → balance = 21950.
    // Old cumulative-sum logic would see sumDeposits = 22950 and block any new deposit.
    // New balance-based logic: 21950 + 1000 = 22950 = limit → must not throw.
    FinancialAccount account = openAccount(AccountSubType.LIVRET_A);
    account.recordTransaction(deposit("22950"));
    account.recordTransaction(withdrawal("1000")); // balance now 21950

    AccountBusinessRules.validateDeposit(
        account, new Money(new BigDecimal("1000"), EUR), List.of(account));
  }

  @Test
  void validateDeposit_livretA_uses_current_balance_not_cumulative_deposits() {
    // Deposit 22900, then withdraw 900 → balance = 22000.
    // Old logic: sumDeposits = 22900, 22900 + 500 = 23400 > 22950 → would block (wrong).
    // New logic: balance = 22000, 22000 + 500 = 22500 < 22950 → must not throw.
    FinancialAccount account = openAccount(AccountSubType.LIVRET_A);
    account.recordTransaction(deposit("22900"));
    account.recordTransaction(withdrawal("900")); // balance now 22000

    AccountBusinessRules.validateDeposit(
        account, new Money(new BigDecimal("500"), EUR), List.of(account));
  }

  @Test
  void validateDeposit_livretA_interest_above_cap_blocks_further_deposits() {
    // Deposit 22000, then credit 1000 interest (DIVIDEND) → balance = 23000 > 22950.
    // Old logic: sumDeposits = 22000, 22000 + 500 = 22500 < 22950 → would allow (wrong).
    // New logic: balance = 23000, 23000 + 500 > 22950 → must throw.
    FinancialAccount account = openAccount(AccountSubType.LIVRET_A);
    account.recordTransaction(deposit("22000"));
    account.recordTransaction(dividend("1000")); // balance now 23000

    assertThatThrownBy(
            () ->
                AccountBusinessRules.validateDeposit(
                    account, new Money(new BigDecimal("500"), EUR), List.of(account)))
        .isInstanceOf(DepositLimitExceededException.class);
  }

  @Test
  void validateDeposit_pea_uses_cumulative_deposits_not_balance() {
    // PEA: deposit 140000 then withdraw 60000 → balance = 80000.
    // Balance-based would allow 15000 more (80000 + 15000 = 95000 < 150000).
    // Cumulative-sum correctly blocks: 140000 + 15000 = 155000 > 150000 → must throw.
    FinancialAccount peaAccount = openPeaAccount(AccountSubType.PEA);
    peaAccount.recordTransaction(deposit("140000"));
    peaAccount.recordTransaction(withdrawal("60000")); // balance now 80000

    assertThatThrownBy(
            () ->
                AccountBusinessRules.validateDeposit(
                    peaAccount, new Money(new BigDecimal("15000"), EUR), List.of(peaAccount)))
        .isInstanceOf(DepositLimitExceededException.class)
        .hasMessageContaining("PEA");
  }

  @Test
  void remainingCapacity_livretA_reflects_withdrawal() {
    FinancialAccount account = openAccount(AccountSubType.LIVRET_A);
    account.recordTransaction(deposit("22950"));
    account.recordTransaction(withdrawal("1000")); // balance now 21950

    Optional<Money> remaining = AccountBusinessRules.remainingCapacity(account, List.of(account));

    assertThat(remaining).isPresent();
    assertThat(remaining.get().amount()).isEqualByComparingTo(new BigDecimal("1000"));
  }

  @Test
  void validateDepositAfterEdit_throws_when_balance_exceeds_livretA_limit() {
    FinancialAccount account = openAccount(AccountSubType.LIVRET_A);
    // Post-edit state: balance 23 500€ > 22 950€ cap
    account.recordTransaction(deposit("23500"));

    assertThatThrownBy(
            () -> AccountBusinessRules.validateDepositAfterEdit(account, List.of(account)))
        .isInstanceOf(DepositLimitExceededException.class)
        .hasMessageContaining("LIVRET_A");
  }

  @Test
  void validateDepositAfterEdit_passes_when_balance_within_limit() {
    FinancialAccount account = openAccount(AccountSubType.LIVRET_A);
    account.recordTransaction(deposit("22000"));

    // Must not throw
    AccountBusinessRules.validateDepositAfterEdit(account, List.of(account));
  }

  @Test
  void validateDepositAfterEdit_passes_when_no_subtype() {
    FinancialAccount account =
        FinancialAccount.open(
            "CTO",
            AccountType.COMPTE_TITRES,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            UserId.generate(),
            null,
            TODAY);
    account.recordTransaction(deposit("999999"));

    // Null subType — no limit applies; must not throw
    AccountBusinessRules.validateDepositAfterEdit(account, List.of(account));
  }

  @Test
  void validateDepositAfterEdit_uses_combined_rule_for_pea_pme() {
    UserId userId = UserId.generate();
    FinancialAccount peaAccount =
        FinancialAccount.open(
            "PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            userId,
            AccountSubType.PEA,
            TODAY);
    peaAccount.recordTransaction(deposit("150000"));

    FinancialAccount peaPmeAccount =
        FinancialAccount.open(
            "PEA-PME",
            AccountType.PEA_PME,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            userId,
            AccountSubType.PEA_PME,
            TODAY);
    // Post-edit state: PEA-PME has 80 000€ deposits; combined = 230 000€ > 225 000€
    peaPmeAccount.recordTransaction(deposit("80000"));

    List<FinancialAccount> allAccounts = List.of(peaAccount, peaPmeAccount);

    assertThatThrownBy(
            () -> AccountBusinessRules.validateDepositAfterEdit(peaPmeAccount, allAccounts))
        .isInstanceOf(DepositLimitExceededException.class)
        .hasMessageContaining("PEA_PME");
  }

  @Test
  void depositLimitExceededException_carries_correct_fields() {
    FinancialAccount account = openAccount(AccountSubType.LIVRET_A);
    account.recordTransaction(deposit("22000"));
    Money depositAmount = new Money(new BigDecimal("1000"), EUR);

    DepositLimitExceededException ex = null;
    try {
      AccountBusinessRules.validateDeposit(account, depositAmount, List.of(account));
    } catch (DepositLimitExceededException e) {
      ex = e;
    }

    assertThat(ex).isNotNull();
    assertThat(ex.subType()).isEqualTo(AccountSubType.LIVRET_A);
    assertThat(ex.limit().amount()).isEqualByComparingTo(new BigDecimal("22950"));
    assertThat(ex.currentTotal().amount()).isEqualByComparingTo(new BigDecimal("22000"));
    assertThat(ex.attempted().amount()).isEqualByComparingTo(new BigDecimal("1000"));
    assertThat(ex.remaining().amount()).isEqualByComparingTo(new BigDecimal("950"));
  }
}
