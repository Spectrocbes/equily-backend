package com.equily.portfolio.application;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.application.exception.PeaClosureException;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.FinancialAccountRepository;
import com.equily.portfolio.domain.Holding;
import com.equily.portfolio.domain.PeaWithdrawalSimulation;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionId;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.account.AccountBusinessRules;
import com.equily.portfolio.domain.account.AccountSubType;
import com.equily.portfolio.domain.exception.AccountNotFoundException;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class PeaClosureService implements PeaClosureUseCase {

  // French flat tax rates (Loi Pacte)
  private static final BigDecimal IR_RATE = new BigDecimal("0.128");
  private static final BigDecimal PS_RATE = new BigDecimal("0.186");
  private static final Currency EUR = Currency.getInstance("EUR");

  private final FinancialAccountRepository repository;

  PeaClosureService(FinancialAccountRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public PeaWithdrawalSimulation simulate(
      FinancialAccountId accountId,
      UserId userId,
      BigDecimal withdrawalAmount,
      BigDecimal livePortfolioValue) {
    FinancialAccount account = getAccount(accountId, userId);
    validateIsPea(account);
    return simulateFor(account, withdrawalAmount, livePortfolioValue);
  }

  @Override
  public void closePea(FinancialAccountId accountId, UserId userId, BigDecimal livePortfolioValue) {
    FinancialAccount account = getAccount(accountId, userId);
    validateIsPea(account);

    List<Holding> holdings = Holding.computeFrom(account.transactions());
    if (!holdings.isEmpty()) {
      throw new PeaClosureException(
          "Cannot close PEA with open holdings. Sell all positions first.");
    }

    PeaWithdrawalSimulation sim = simulateFor(account, null, livePortfolioValue);

    // Transaction 1: net amount received by the user — store liqValue + gross for Loi Pacte replay
    if (sim.netAmount().compareTo(BigDecimal.ZERO) > 0) {
      account.recordTransaction(
          Transaction.of(
              TransactionId.generate(),
              TransactionType.WITHDRAWAL,
              null,
              null,
              null,
              new Money(sim.netAmount(), EUR),
              LocalDate.now(),
              BigDecimal.ZERO,
              "PEA closure — net amount after taxes",
              "EUR",
              sim.netAmount(),
              BigDecimal.ONE,
              sim.liquidationValue(),
              sim.withdrawalAmount(),
              null,
              null,
              null,
              null));
    }

    // Transaction 2: flat tax collected (only if taxes > 0)
    if (sim.totalTax().compareTo(BigDecimal.ZERO) > 0) {
      account.recordTransaction(
          Transaction.ofEur(
              TransactionId.generate(),
              TransactionType.WITHDRAWAL,
              null,
              null,
              null,
              new Money(sim.totalTax(), EUR),
              LocalDate.now(),
              BigDecimal.ZERO,
              String.format(
                  "PEA closure — flat tax (IR: %.2f€, PS: %.2f€)", sim.irTax(), sim.psTax())));
    }

    account.close(LocalDate.now());
    repository.save(account);
  }

  /**
   * Core tax simulation per Loi Pacte:
   *
   * <p>&lt; 5 years: full closure required. taxableGain = liquidationValue - totalDeposits. irTax =
   * taxableGain × 12.8%. psTax = taxableGain × 18.6%.
   *
   * <p>&ge; 5 years: partial withdrawal allowed, IR exempt. gainRatio = 1 - (totalDeposits /
   * liquidationValue). taxableGain = withdrawalAmount × gainRatio. psTax = taxableGain × 18.6%.
   * irTax = 0.
   */
  private PeaWithdrawalSimulation simulateFor(
      FinancialAccount account, BigDecimal withdrawalAmount, BigDecimal livePortfolioValue) {

    BigDecimal liquidationValue =
        livePortfolioValue.add(account.balance().amount()).setScale(2, RoundingMode.HALF_EVEN);

    BigDecimal totalDeposits =
        AccountBusinessRules.computeAdjustedTotalDeposits(account)
            .setScale(2, RoundingMode.HALF_EVEN);

    BigDecimal netGain = liquidationValue.subtract(totalDeposits);
    boolean atLoss = netGain.compareTo(BigDecimal.ZERO) <= 0;
    boolean older5y = AccountBusinessRules.isPeaOlderThan5Years(account);

    // gainRatio = 1 - (deposits / liquidative); capped at 0 to avoid negatives
    BigDecimal gainRatio =
        liquidationValue.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : BigDecimal.ONE
                .subtract(totalDeposits.divide(liquidationValue, 6, RoundingMode.HALF_EVEN))
                .max(BigDecimal.ZERO);

    BigDecimal effectiveAmount;
    BigDecimal taxableGain;
    BigDecimal irTax;
    BigDecimal psTax;

    if (!older5y) {
      // < 5 years: full closure, flat tax 30% (IR 12.8% + PS 18.6%) on gain
      effectiveAmount = liquidationValue;
      taxableGain = atLoss ? BigDecimal.ZERO : netGain.setScale(2, RoundingMode.HALF_EVEN);
      irTax =
          atLoss
              ? BigDecimal.ZERO
              : taxableGain.multiply(IR_RATE).setScale(2, RoundingMode.HALF_EVEN);
      psTax =
          atLoss
              ? BigDecimal.ZERO
              : taxableGain.multiply(PS_RATE).setScale(2, RoundingMode.HALF_EVEN);
    } else {
      // >= 5 years: partial withdrawal allowed, IR exempt, PS on gain portion only
      effectiveAmount = withdrawalAmount != null ? withdrawalAmount : liquidationValue;
      taxableGain =
          atLoss
              ? BigDecimal.ZERO
              : effectiveAmount.multiply(gainRatio).setScale(2, RoundingMode.HALF_EVEN);
      irTax = BigDecimal.ZERO;
      psTax =
          atLoss
              ? BigDecimal.ZERO
              : taxableGain.multiply(PS_RATE).setScale(2, RoundingMode.HALF_EVEN);
    }

    BigDecimal totalTax = irTax.add(psTax);
    BigDecimal netAmount = effectiveAmount.subtract(totalTax).setScale(2, RoundingMode.HALF_EVEN);

    return new PeaWithdrawalSimulation(
        liquidationValue,
        totalDeposits,
        netGain.setScale(2, RoundingMode.HALF_EVEN),
        gainRatio,
        atLoss,
        older5y,
        effectiveAmount.setScale(2, RoundingMode.HALF_EVEN),
        taxableGain,
        irTax,
        psTax,
        totalTax,
        netAmount);
  }

  private FinancialAccount getAccount(FinancialAccountId accountId, UserId userId) {
    FinancialAccount account =
        repository.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    if (!account.ownerId().equals(userId)) {
      throw new AccountNotFoundException(accountId);
    }
    return account;
  }

  private static void validateIsPea(FinancialAccount account) {
    if (account.subType() != AccountSubType.PEA && account.subType() != AccountSubType.PEA_PME) {
      throw new IllegalArgumentException("Account is not a PEA or PEA-PME");
    }
  }
}
