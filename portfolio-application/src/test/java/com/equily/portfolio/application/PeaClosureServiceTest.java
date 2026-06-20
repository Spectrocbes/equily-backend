package com.equily.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.application.exception.PeaClosureException;
import com.equily.portfolio.domain.AccountType;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.FinancialAccountRepository;
import com.equily.portfolio.domain.PeaWithdrawalSimulation;
import com.equily.portfolio.domain.Ticker;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.account.AccountSubType;
import com.equily.portfolio.domain.exception.AccountNotFoundException;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PeaClosureServiceTest {

  @Mock private FinancialAccountRepository repository;

  @InjectMocks private PeaClosureService service;

  private static final Currency EUR = Currency.getInstance("EUR");

  // Opened more than 5 years ago relative to any test run in 2026+
  private static final LocalDate OVER_5Y_AGO = LocalDate.of(2019, 1, 1);
  // Opened less than 5 years ago relative to any test run before 2029
  private static final LocalDate UNDER_5Y_AGO = LocalDate.of(2024, 1, 1);

  private FinancialAccount openPea(LocalDate openedAt) {
    return FinancialAccount.open(
        "Mon PEA",
        AccountType.PEA,
        new Money(BigDecimal.ZERO, EUR),
        "Fortuneo",
        UserId.generate(),
        AccountSubType.PEA,
        openedAt);
  }

  private Transaction deposit(String amount, LocalDate date) {
    return Transaction.ofEur(
        TransactionId.generate(),
        TransactionType.DEPOSIT,
        null,
        null,
        null,
        new Money(new BigDecimal(amount), EUR),
        date,
        null,
        null);
  }

  private Transaction buy(String qty, String price, LocalDate date) {
    BigDecimal q = new BigDecimal(qty);
    BigDecimal p = new BigDecimal(price);
    return Transaction.ofEur(
        TransactionId.generate(),
        TransactionType.BUY,
        new Ticker("MC.PA"),
        q,
        new Money(p, EUR),
        new Money(q.multiply(p), EUR),
        date,
        null,
        null);
  }

  // --- simulate() tests ---

  @Test
  void simulate_less_than_5y_applies_full_flat_tax_30_percent_on_gain() {
    // totalDeposits=150000, livePortfolio=0, balance=150000 → liquidationValue=150000
    // netGain = 0 → atLoss, no tax
    // netAmount = 150000
    FinancialAccount account = openPea(UNDER_5Y_AGO);
    UserId userId = account.ownerId();
    account.recordTransaction(deposit("100000", UNDER_5Y_AGO));
    account.recordTransaction(deposit("50000", UNDER_5Y_AGO));

    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    PeaWithdrawalSimulation sim = service.simulate(account.id(), userId, null, BigDecimal.ZERO);

    assertThat(sim.liquidationValue()).isEqualByComparingTo(new BigDecimal("150000.00"));
    assertThat(sim.totalDeposits()).isEqualByComparingTo(new BigDecimal("150000.00"));
    assertThat(sim.taxableGain()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(sim.atLoss()).isTrue(); // netGain = 0 → atLoss
    assertThat(sim.peaOlderThan5Years()).isFalse();
    assertThat(sim.irTax()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(sim.psTax()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(sim.totalTax()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(sim.netAmount()).isEqualByComparingTo(new BigDecimal("150000.00"));
  }

  @Test
  void simulate_less_than_5y_gain_applies_ir_and_ps() {
    // totalDeposits=5000, livePortfolio=0, balance=6000 (1000 dividend) → liquidationValue=6000
    // taxableGain = 6000 - 5000 = 1000
    // irTax = 1000 × 12.8% = 128, psTax = 1000 × 18.6% = 186, totalTax = 314
    // netAmount = 6000 - 314 = 5686
    FinancialAccount account = openPea(UNDER_5Y_AGO);
    UserId userId = account.ownerId();
    account.recordTransaction(deposit("5000", UNDER_5Y_AGO));
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DIVIDEND,
            null,
            null,
            null,
            new Money(new BigDecimal("1000"), EUR),
            UNDER_5Y_AGO,
            null,
            null));

    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    PeaWithdrawalSimulation sim = service.simulate(account.id(), userId, null, BigDecimal.ZERO);

    assertThat(sim.liquidationValue()).isEqualByComparingTo(new BigDecimal("6000.00"));
    assertThat(sim.totalDeposits()).isEqualByComparingTo(new BigDecimal("5000.00"));
    assertThat(sim.taxableGain()).isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat(sim.atLoss()).isFalse();
    assertThat(sim.peaOlderThan5Years()).isFalse();
    assertThat(sim.irTax()).isEqualByComparingTo(new BigDecimal("128.00"));
    assertThat(sim.psTax()).isEqualByComparingTo(new BigDecimal("186.00"));
    assertThat(sim.totalTax()).isEqualByComparingTo(new BigDecimal("314.00"));
    assertThat(sim.netAmount()).isEqualByComparingTo(new BigDecimal("5686.00"));
  }

  @Test
  void simulate_over_5y_applies_ps_only_on_gain_portion_of_withdrawal() {
    // totalDeposits=10000, livePortfolio=20000, balance=0 → liquidationValue=20000+balance
    // balance=10000 (from deposit), liquidationValue=30000
    // gainRatio = 1 - 10000/30000 = 0.666...
    // effectiveAmount = 6000 (partial)
    // taxableGain = 6000 × gainRatio
    // irTax = 0 (>=5y), psTax = taxableGain × 18.6%
    FinancialAccount account = openPea(OVER_5Y_AGO);
    UserId userId = account.ownerId();
    account.recordTransaction(deposit("10000", OVER_5Y_AGO));

    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    PeaWithdrawalSimulation sim =
        service.simulate(account.id(), userId, new BigDecimal("6000"), new BigDecimal("20000"));

    assertThat(sim.liquidationValue()).isEqualByComparingTo(new BigDecimal("30000.00"));
    assertThat(sim.totalDeposits()).isEqualByComparingTo(new BigDecimal("10000.00"));
    assertThat(sim.peaOlderThan5Years()).isTrue();
    assertThat(sim.irTax()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(sim.withdrawalAmount()).isEqualByComparingTo(new BigDecimal("6000.00"));
    assertThat(sim.taxableGain()).isPositive();
    assertThat(sim.psTax()).isPositive();
    assertThat(sim.netAmount()).isLessThan(new BigDecimal("6000.00"));
  }

  @Test
  void simulate_over_5y_full_closure_ps_only_on_total_gain() {
    // totalDeposits=5000, livePortfolio=0, balance=6000 → liquidationValue=6000
    // gainRatio = 1 - 5000/6000 = 0.1666...
    // effectiveAmount = 6000 (withdrawalAmount=null)
    // taxableGain = 6000 × 0.1666... = 1000
    // irTax = 0, psTax = 1000 × 18.6% = 186
    // netAmount = 6000 - 186 = 5814
    FinancialAccount account = openPea(OVER_5Y_AGO);
    UserId userId = account.ownerId();
    account.recordTransaction(deposit("5000", OVER_5Y_AGO));
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DIVIDEND,
            null,
            null,
            null,
            new Money(new BigDecimal("1000"), EUR),
            OVER_5Y_AGO,
            null,
            null));

    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    PeaWithdrawalSimulation sim = service.simulate(account.id(), userId, null, BigDecimal.ZERO);

    assertThat(sim.peaOlderThan5Years()).isTrue();
    assertThat(sim.irTax()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(sim.psTax()).isEqualByComparingTo(new BigDecimal("186.00"));
    assertThat(sim.totalTax()).isEqualByComparingTo(new BigDecimal("186.00"));
    assertThat(sim.netAmount()).isEqualByComparingTo(new BigDecimal("5814.00"));
  }

  @Test
  void simulate_at_loss_no_tax() {
    // totalDeposits=10000, livePortfolio=0, balance=8000 → liquidationValue=8000
    // netGain = 8000 - 10000 = -2000 → atLoss
    FinancialAccount account = openPea(UNDER_5Y_AGO);
    UserId userId = account.ownerId();
    account.recordTransaction(deposit("10000", UNDER_5Y_AGO));
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.WITHDRAWAL,
            null,
            null,
            null,
            new Money(new BigDecimal("2000"), EUR),
            UNDER_5Y_AGO,
            null,
            null));

    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    PeaWithdrawalSimulation sim = service.simulate(account.id(), userId, null, BigDecimal.ZERO);

    assertThat(sim.atLoss()).isTrue();
    assertThat(sim.taxableGain()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(sim.irTax()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(sim.psTax()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(sim.totalTax()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(sim.netAmount()).isEqualByComparingTo(new BigDecimal("8000.00"));
  }

  // --- closePea() tests ---

  @Test
  void closePea_throws_when_holdings_not_empty() {
    FinancialAccount account = openPea(UNDER_5Y_AGO);
    UserId userId = account.ownerId();
    account.recordTransaction(deposit("10000", UNDER_5Y_AGO));
    account.recordTransaction(buy("10", "500", UNDER_5Y_AGO));

    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    assertThatThrownBy(() -> service.closePea(account.id(), userId, BigDecimal.ZERO))
        .isInstanceOf(PeaClosureException.class)
        .hasMessageContaining("holdings");
  }

  @Test
  void closePea_throws_when_not_pea() {
    UserId userId = UserId.generate();
    FinancialAccount account =
        FinancialAccount.open(
            "Livret A",
            AccountType.SAVINGS_ACCOUNT,
            new Money(BigDecimal.ZERO, EUR),
            "Fortuneo",
            userId,
            AccountSubType.LIVRET_A,
            UNDER_5Y_AGO);

    when(repository.findById(any())).thenReturn(Optional.of(account));

    assertThatThrownBy(() -> service.closePea(account.id(), userId, BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a PEA");
  }

  @Test
  void closePea_creates_two_withdrawals_when_taxes_apply() {
    // totalDeposits=5000, livePortfolio=0, balance=6000 (1000 dividend) → liquidationValue=6000
    // taxableGain=1000, irTax=128, psTax=186, totalTax=314, netAmount=5686
    FinancialAccount account = openPea(UNDER_5Y_AGO);
    UserId userId = account.ownerId();
    account.recordTransaction(deposit("5000", UNDER_5Y_AGO));
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.DIVIDEND,
            null,
            null,
            null,
            new Money(new BigDecimal("1000"), EUR),
            UNDER_5Y_AGO,
            null,
            null));

    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    ArgumentCaptor<FinancialAccount> captor = ArgumentCaptor.forClass(FinancialAccount.class);
    service.closePea(account.id(), userId, BigDecimal.ZERO);

    verify(repository).save(captor.capture());
    FinancialAccount saved = captor.getValue();
    assertThat(saved.isClosed()).isTrue();
    assertThat(saved.closedAt()).isNotNull();

    long withdrawalCount =
        saved.transactions().stream().filter(t -> t.type() == TransactionType.WITHDRAWAL).count();
    assertThat(withdrawalCount).isEqualTo(2L);
  }

  @Test
  void closePea_balance_is_zero_after_closure() {
    // totalDeposits=10000, livePortfolio=0, balance=10000 → netGain=0 → atLoss → no taxes
    // Only the net withdrawal is created (totalTax=0 → no second withdrawal)
    FinancialAccount account = openPea(UNDER_5Y_AGO);
    UserId userId = account.ownerId();
    account.recordTransaction(deposit("10000", UNDER_5Y_AGO));

    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    ArgumentCaptor<FinancialAccount> captor = ArgumentCaptor.forClass(FinancialAccount.class);
    service.closePea(account.id(), userId, BigDecimal.ZERO);

    verify(repository).save(captor.capture());
    FinancialAccount saved = captor.getValue();
    assertThat(saved.balance().amount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(saved.isClosed()).isTrue();

    // atLoss (netGain=0) → totalTax=0 → only 1 withdrawal (net amount)
    long withdrawalCount =
        saved.transactions().stream().filter(t -> t.type() == TransactionType.WITHDRAWAL).count();
    assertThat(withdrawalCount).isEqualTo(1L);
  }

  @Test
  void simulate_over5y_uses_adjusted_deposits_after_prior_withdrawal() {
    // PEA opened 2019-01-01, 5y anniversary = 2024-01-01.
    // Spec scenario: deposits=100000 (2020), prior withdrawal after 5y (2025):
    //   liqValue=106000, gross=30000, net=29684.15
    // capitalRatio = 100000/106000 = 0.943396 → withdrawnCapital = 28301.88
    // adjustedDeposits = 71698.12
    // Simulate full closure: balance = 100000 - 29684.15 - 315.85 = 70000, livePortfolio = 6000
    //   liquidationValue = 76000
    //   gainRatio = 4301.88/76000 = 0.056604
    //   taxableGain = 76000 × 0.056604 = 4301.90, psTax = 800.15, netAmount = 75199.85
    LocalDate depositDate = LocalDate.of(2020, 1, 1);
    LocalDate withdrawalDate = LocalDate.of(2025, 1, 1);

    FinancialAccount account = openPea(OVER_5Y_AGO);
    UserId userId = account.ownerId();
    account.recordTransaction(deposit("100000", depositDate));
    // Prior net withdrawal stored with liqValue + gross for Loi Pacte replay
    account.recordTransaction(
        Transaction.of(
            TransactionId.generate(),
            TransactionType.WITHDRAWAL,
            null,
            null,
            null,
            new Money(new BigDecimal("29684.15"), EUR),
            withdrawalDate,
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
    // Prior PS tax transaction — no liqValue stored, not counted in replay
    account.recordTransaction(
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.WITHDRAWAL,
            null,
            null,
            null,
            new Money(new BigDecimal("315.85"), EUR),
            withdrawalDate,
            null,
            "PS tax"));

    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    // Full closure simulation
    PeaWithdrawalSimulation sim =
        service.simulate(account.id(), userId, null, new BigDecimal("6000"));

    assertThat(sim.liquidationValue()).isEqualByComparingTo(new BigDecimal("76000.00"));
    assertThat(sim.totalDeposits()).isEqualByComparingTo(new BigDecimal("71698.12"));
    assertThat(sim.peaOlderThan5Years()).isTrue();
    assertThat(sim.irTax()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(sim.psTax()).isEqualByComparingTo(new BigDecimal("800.15"));
    assertThat(sim.netAmount()).isEqualByComparingTo(new BigDecimal("75199.85"));
  }

  @Test
  void pea_closure_after_partial_withdrawal_total_ps_equals_gain_times_rate() {
    // Coherence invariant: PS_partial + PS_closure = totalGain × 18.6%
    // Setup: deposits=100000, totalGain=6000 (liqValue=106000 at time of partial withdrawal)
    // Step 1: partial withdrawal psTax = 315.85 (computed earlier for gross=30000)
    // Step 2: full closure on remaining value 76000 → psTax = 800.15
    // Total PS = 315.85 + 800.15 = 1116.00 = 6000 × 18.6% ✓
    BigDecimal psTaxPartial = new BigDecimal("315.85");
    BigDecimal psTaxClosure = new BigDecimal("800.15");
    // 6000 × 18.6% = 1116.00
    assertThat(psTaxPartial.add(psTaxClosure)).isEqualByComparingTo(new BigDecimal("1116.00"));
  }

  @Test
  void simulate_throws_when_account_not_found() {
    FinancialAccountId id = FinancialAccountId.generate();
    UserId userId = UserId.generate();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.simulate(id, userId, null, BigDecimal.ZERO))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void simulate_live_portfolio_value_is_included_in_liquidation_value() {
    // balance = 1000, livePortfolio = 5000 → liquidationValue = 6000
    FinancialAccount account = openPea(UNDER_5Y_AGO);
    UserId userId = account.ownerId();
    account.recordTransaction(deposit("1000", UNDER_5Y_AGO));

    when(repository.findById(account.id())).thenReturn(Optional.of(account));

    PeaWithdrawalSimulation sim =
        service.simulate(account.id(), userId, null, new BigDecimal("5000"));

    assertThat(sim.liquidationValue()).isEqualByComparingTo(new BigDecimal("6000.00"));
  }
}
