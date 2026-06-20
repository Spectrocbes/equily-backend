package com.equily.portfolio.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.AccountType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.TransferDirection;
import com.equily.portfolio.domain.exception.AccountCardinalityException;
import com.equily.portfolio.domain.exception.DepositLimitExceededException;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

  private Transaction dividend(String amount) {
    return Transaction.ofEur(
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
  void validateDeposit_pea_over_5y_withdrawal_liberates_capacity() {
    // PEA opened 2019-01-01 (>5y from any date in 2026+). Deposit 140000 on 2020-01-01, then
    // withdraw 30000 after the 5y anniversary (2024-01-01). The Loi Pacte replay algorithm:
    // replay in date order → deposit 140000, then withdrawal reduces runningDeposits by 30000
    // → used = 110000. Trying to add 45000 → 155000 > 150000 → throws.
    // But adding 40000 → 150000 == limit → passes.
    LocalDate openedAt = LocalDate.of(2019, 1, 1);
    FinancialAccount peaAccount =
        FinancialAccount.open(
            "PEA Account",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            UserId.generate(),
            AccountSubType.PEA,
            openedAt);
    // Deposit dated before 5y anniversary — must come first chronologically for correct replay
    Transaction depositTx =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("140000"), EUR),
            LocalDate.of(2020, 1, 1),
            null,
            null);
    peaAccount.recordTransaction(depositTx);
    // Withdrawal dated after 5y anniversary (2024-01-01) — store liqValue=140000 (no gain),
    // gross=30000 so the exact Loi Pacte formula gives withdrawnCapital=30000 (capitalRatio=1)
    Transaction withdrawalAfter5y =
        Transaction.of(
            TransactionId.generate(),
            TransactionType.WITHDRAWAL,
            null,
            null,
            null,
            new Money(new BigDecimal("30000"), EUR),
            LocalDate.of(2024, 6, 1),
            BigDecimal.ZERO,
            "PEA withdrawal",
            "EUR",
            new BigDecimal("30000"),
            BigDecimal.ONE,
            new BigDecimal("140000"),
            new BigDecimal("30000"),
            null,
            null,
            null,
            null);
    peaAccount.recordTransaction(withdrawalAfter5y);

    // used = 110000 → 40000 more → 150000 == limit → OK
    AccountBusinessRules.validateDeposit(
        peaAccount, new Money(new BigDecimal("40000"), EUR), List.of(peaAccount));

    // 45000 would put it at 155000 > 150000 → should throw
    assertThatThrownBy(
            () ->
                AccountBusinessRules.validateDeposit(
                    peaAccount, new Money(new BigDecimal("45000"), EUR), List.of(peaAccount)))
        .isInstanceOf(DepositLimitExceededException.class);
  }

  @Test
  void validateDeposit_pea_under_5y_withdrawal_does_not_liberate_capacity() {
    // PEA opened 2024-01-01 (<5y from 2026). Deposit 140000, withdraw 60000 → balance 80000.
    // Cumulative deposits = 140000, so 15000 more → 155000 > 150000 → throws.
    LocalDate openedAt = LocalDate.of(2024, 1, 1);
    FinancialAccount peaAccount =
        FinancialAccount.open(
            "PEA Account",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            UserId.generate(),
            AccountSubType.PEA,
            openedAt);
    peaAccount.recordTransaction(deposit("140000"));
    peaAccount.recordTransaction(withdrawal("60000")); // balance = 80000

    assertThatThrownBy(
            () ->
                AccountBusinessRules.validateDeposit(
                    peaAccount, new Money(new BigDecimal("15000"), EUR), List.of(peaAccount)))
        .isInstanceOf(DepositLimitExceededException.class)
        .hasMessageContaining("PEA");
  }

  @Test
  void pea_capacity_over_5y_withdrawal_reduces_versements_counter() {
    // PEA opened 2019-01-01 (>5y). Deposit 140000 on 2020-01-01, withdraw 30000 after 5y.
    // Loi Pacte replay: runningDeposits=140000 → withdrawal reduces by 30000 → 110000.
    // remainingCapacity = 150000 - 110000 = 40000.
    LocalDate openedAt = LocalDate.of(2019, 1, 1);
    FinancialAccount peaAccount =
        FinancialAccount.open(
            "PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            UserId.generate(),
            AccountSubType.PEA,
            openedAt);
    peaAccount.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("140000"), EUR),
            LocalDate.of(2020, 1, 1),
            null,
            null));
    // liqValue=140000 (no gain) → capitalRatio=1 → withdrawnCapital=30000 → remaining=40000
    peaAccount.recordTransaction(
        Transaction.of(
            TransactionId.generate(),
            TransactionType.WITHDRAWAL,
            null,
            null,
            null,
            new Money(new BigDecimal("30000"), EUR),
            LocalDate.of(2024, 6, 1),
            BigDecimal.ZERO,
            "PEA withdrawal",
            "EUR",
            new BigDecimal("30000"),
            BigDecimal.ONE,
            new BigDecimal("140000"),
            new BigDecimal("30000"),
            null,
            null,
            null,
            null));

    Optional<Money> remaining =
        AccountBusinessRules.remainingCapacity(peaAccount, List.of(peaAccount));

    assertThat(remaining).isPresent();
    assertThat(remaining.get().amount()).isEqualByComparingTo(new BigDecimal("40000"));
  }

  @Test
  void pea_capacity_under_5y_withdrawal_does_not_reduce_capacity() {
    // PEA opened 2024-01-01 (<5y). Deposit 140000, withdraw 60000.
    // Cumulative deposits = 140000 (withdrawal has no effect on capacity).
    // remainingCapacity = 150000 - 140000 = 10000.
    LocalDate openedAt = LocalDate.of(2024, 1, 1);
    FinancialAccount peaAccount =
        FinancialAccount.open(
            "PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            UserId.generate(),
            AccountSubType.PEA,
            openedAt);
    peaAccount.recordTransaction(deposit("140000"));
    peaAccount.recordTransaction(withdrawal("60000")); // balance = 80000, but deposits = 140000

    Optional<Money> remaining =
        AccountBusinessRules.remainingCapacity(peaAccount, List.of(peaAccount));

    assertThat(remaining).isPresent();
    assertThat(remaining.get().amount()).isEqualByComparingTo(new BigDecimal("10000"));
  }

  @Test
  void computeAdjustedTotalDeposits_spec_scenario() {
    // deposits=100000, liqValue=106000, gross=30000
    // capitalRatio = 100000/106000 = 0.943396 (6dp)
    // withdrawnCapital = 30000 × 0.943396 = 28301.88
    // adjustedDeposits = 100000 - 28301.88 = 71698.12
    // remainingCapacity = 150000 - 71698.12 = 78301.88
    LocalDate openedAt = LocalDate.of(2019, 1, 1);
    FinancialAccount pea =
        FinancialAccount.open(
            "PEA",
            AccountType.PEA,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            UserId.generate(),
            AccountSubType.PEA,
            openedAt);
    pea.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DEPOSIT,
            null,
            null,
            null,
            new Money(new BigDecimal("100000"), EUR),
            LocalDate.of(2020, 1, 1),
            null,
            null));
    pea.recordTransaction(
        Transaction.of(
            TransactionId.generate(),
            TransactionType.WITHDRAWAL,
            null,
            null,
            null,
            new Money(new BigDecimal("29684.15"), EUR),
            LocalDate.of(2025, 1, 1),
            BigDecimal.ZERO,
            "PEA withdrawal (after PS)",
            "EUR",
            new BigDecimal("29684.15"),
            BigDecimal.ONE,
            new BigDecimal("106000"),
            new BigDecimal("30000"),
            null,
            null,
            null,
            null));

    java.math.BigDecimal adjusted = AccountBusinessRules.computeAdjustedTotalDeposits(pea);

    assertThat(adjusted).isEqualByComparingTo(new BigDecimal("71698.12"));

    Optional<Money> remaining = AccountBusinessRules.remainingCapacity(pea, List.of(pea));
    assertThat(remaining).isPresent();
    assertThat(remaining.get().amount()).isEqualByComparingTo(new BigDecimal("78301.88"));
  }

  @Test
  void validateCardinality_throws_when_livret_a_already_exists() {
    FinancialAccount existing = openAccount(AccountSubType.LIVRET_A);
    assertThatThrownBy(
            () ->
                AccountBusinessRules.validateCardinality(
                    AccountSubType.LIVRET_A, List.of(existing)))
        .isInstanceOf(AccountCardinalityException.class)
        .hasMessageContaining("LIVRET_A");
  }

  @Test
  void validateCardinality_throws_when_pea_already_exists() {
    FinancialAccount existing = openPeaAccount(AccountSubType.PEA);
    assertThatThrownBy(
            () -> AccountBusinessRules.validateCardinality(AccountSubType.PEA, List.of(existing)))
        .isInstanceOf(AccountCardinalityException.class)
        .hasMessageContaining("PEA");
  }

  @Test
  void validateCardinality_allows_pea_and_pea_pme_together() {
    FinancialAccount existingPea = openPeaAccount(AccountSubType.PEA);
    // PEA_PME is a different sub-type — should not be blocked by existing PEA
    AccountBusinessRules.validateCardinality(AccountSubType.PEA_PME, List.of(existingPea));
  }

  @Test
  void validateCardinality_throws_when_pea_pme_already_exists() {
    FinancialAccount existing = openPeaAccount(AccountSubType.PEA_PME);
    assertThatThrownBy(
            () ->
                AccountBusinessRules.validateCardinality(AccountSubType.PEA_PME, List.of(existing)))
        .isInstanceOf(AccountCardinalityException.class)
        .hasMessageContaining("PEA_PME");
  }

  @Test
  void validateCardinality_ignores_closed_accounts() {
    FinancialAccount closed = openAccount(AccountSubType.LIVRET_A);
    closed.close(TODAY);
    // Closed account must not count toward the cardinality limit
    AccountBusinessRules.validateCardinality(AccountSubType.LIVRET_A, List.of(closed));
  }

  @Test
  void validateCardinality_no_limit_for_assurance_vie() {
    FinancialAccount existing = openAccount(AccountSubType.ASSURANCE_VIE);
    // ASSURANCE_VIE is not in the single-instance set — multiple are allowed
    AccountBusinessRules.validateCardinality(AccountSubType.ASSURANCE_VIE, List.of(existing));
  }

  private Transaction incomingTransfer(String amount) {
    return Transaction.ofTransfer(
        TransactionId.generate(),
        new Money(new BigDecimal(amount), EUR),
        TODAY,
        "Transfer in",
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        new BigDecimal(amount),
        BigDecimal.ONE,
        TransferDirection.INCOMING);
  }

  private Transaction outgoingTransfer(String amount) {
    return Transaction.ofTransfer(
        TransactionId.generate(),
        new Money(new BigDecimal(amount), EUR),
        TODAY,
        "Transfer out",
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        new BigDecimal(amount),
        BigDecimal.ONE,
        TransferDirection.OUTGOING);
  }

  @Test
  void sumDeposits_counts_transfer_in_as_deposit() {
    // PEA: 50 000 explicit deposit + 10 000 INCOMING transfer = 60 000 used.
    // remainingCapacity = 150 000 - 60 000 = 90 000.
    FinancialAccount pea = openPeaAccount(AccountSubType.PEA);
    pea.recordTransaction(deposit("50000"));
    pea.recordTransaction(incomingTransfer("10000"));

    Optional<Money> remaining = AccountBusinessRules.remainingCapacity(pea, List.of(pea));
    assertThat(remaining).isPresent();
    assertThat(remaining.get().amount()).isEqualByComparingTo(new BigDecimal("90000"));
  }

  @Test
  void sumDeposits_does_not_count_transfer_out() {
    // PEA: 50 000 deposit + OUTGOING transfer (should NOT affect deposit counter).
    // remainingCapacity = 150 000 - 50 000 = 100 000.
    FinancialAccount pea = openPeaAccount(AccountSubType.PEA);
    pea.recordTransaction(deposit("50000"));
    pea.recordTransaction(outgoingTransfer("5000"));

    Optional<Money> remaining = AccountBusinessRules.remainingCapacity(pea, List.of(pea));
    assertThat(remaining).isPresent();
    assertThat(remaining.get().amount()).isEqualByComparingTo(new BigDecimal("100000"));
  }

  @Test
  void pea_capacity_updated_after_transfer_in_from_checking() {
    // Without the fix, TRANSFER INCOMING is invisible → 140 000 used → 15 000 allowed.
    // With the fix, 140 000 deposit + 20 000 transfer-in = 160 000 > 150 000 → throws.
    FinancialAccount pea = openPeaAccount(AccountSubType.PEA);
    pea.recordTransaction(deposit("140000"));
    pea.recordTransaction(incomingTransfer("20000"));

    assertThatThrownBy(
            () ->
                AccountBusinessRules.validateDeposit(
                    pea, new Money(new BigDecimal("1"), EUR), List.of(pea)))
        .isInstanceOf(DepositLimitExceededException.class)
        .hasMessageContaining("PEA");
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
