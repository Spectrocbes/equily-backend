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
        subType);
  }

  private FinancialAccount openPeaAccount(AccountSubType subType) {
    return FinancialAccount.open(
        "PEA Account",
        AccountType.PEA,
        new Money(new BigDecimal("5000"), EUR),
        "Fortuneo",
        UserId.generate(),
        subType);
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

  @Test
  void validateDeposit_noOp_when_subType_is_null() {
    FinancialAccount account =
        FinancialAccount.open(
            "CTO",
            AccountType.COMPTE_TITRES,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            UserId.generate(),
            null);
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
            AccountSubType.PEA);
    peaAccount.recordTransaction(deposit("150000"));

    FinancialAccount peaPmeAccount =
        FinancialAccount.open(
            "PEA-PME",
            AccountType.PEA_PME,
            new Money(new BigDecimal("5000"), EUR),
            "Fortuneo",
            userId,
            AccountSubType.PEA_PME);
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
            null);

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
            null);

    boolean approaching = AccountBusinessRules.isApproachingLimit(account, List.of(account));

    assertThat(approaching).isFalse();
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
