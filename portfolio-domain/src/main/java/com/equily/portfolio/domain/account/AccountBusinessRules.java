package com.equily.portfolio.domain.account;

import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.exception.DepositLimitExceededException;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

/**
 * Domain service enforcing French regulatory rules for financial accounts. Pure domain logic — no
 * Spring, no JPA, no HTTP.
 *
 * <p>Rules implemented:
 *
 * <ul>
 *   <li>Deposit limits per AccountSubType (Livret A, LDDS, LEP, PEA, etc.)
 *   <li>Combined PEA + PEA-PME limit (225 000 €)
 *   <li>Warning threshold at 90% of limit
 * </ul>
 */
public final class AccountBusinessRules {

  private AccountBusinessRules() {}

  /**
   * Validates a DEPOSIT transaction against regulatory limits. No-op for non-DEPOSIT transactions
   * or accounts with no subType.
   *
   * @param account the target account
   * @param depositAmount the amount being deposited
   * @param allUserAccounts all accounts belonging to the user (needed for combined PEA+PEA-PME
   *     check)
   * @throws DepositLimitExceededException if the deposit would exceed the limit
   */
  public static void validateDeposit(
      FinancialAccount account, Money depositAmount, List<FinancialAccount> allUserAccounts) {

    AccountSubType subType = account.subType();
    if (subType == null) return;

    Optional<Money> limitOpt = DepositLimits.limitFor(subType);
    if (limitOpt.isEmpty()) return;

    Money limit = limitOpt.get();
    Money currentTotal = computeDepositTotal(account, allUserAccounts, subType);
    Money newTotal = new Money(currentTotal.amount().add(depositAmount.amount()), limit.currency());

    if (newTotal.amount().compareTo(limit.amount()) > 0) {
      throw new DepositLimitExceededException(subType, limit, currentTotal, depositAmount);
    }
  }

  /** Returns the remaining deposit capacity for an account. Returns empty if no limit applies. */
  public static Optional<Money> remainingCapacity(
      FinancialAccount account, List<FinancialAccount> allUserAccounts) {

    AccountSubType subType = account.subType();
    if (subType == null) return Optional.empty();

    return DepositLimits.limitFor(subType)
        .map(
            limit -> {
              Money currentTotal = computeDepositTotal(account, allUserAccounts, subType);
              BigDecimal remaining =
                  limit.amount().subtract(currentTotal.amount()).max(BigDecimal.ZERO);
              return new Money(remaining, limit.currency());
            });
  }

  /** Returns true if cumulative deposits exceed 90% of the limit. */
  public static boolean isApproachingLimit(
      FinancialAccount account, List<FinancialAccount> allUserAccounts) {

    AccountSubType subType = account.subType();
    if (subType == null) return false;

    return DepositLimits.limitFor(subType)
        .map(
            limit -> {
              Money currentTotal = computeDepositTotal(account, allUserAccounts, subType);
              if (limit.amount().compareTo(BigDecimal.ZERO) == 0) return false;
              BigDecimal ratio =
                  currentTotal.amount().divide(limit.amount(), 4, RoundingMode.HALF_EVEN);
              return ratio.compareTo(DepositLimits.WARNING_THRESHOLD_PERCENT) >= 0;
            })
        .orElse(false);
  }

  /**
   * Computes the deposit capacity used for limit calculation.
   *
   * <p>Savings accounts (Livret A, LDDS, LDD, LEP, Livret Jeune): capacity used = current balance →
   * withdrawals free up space up to the legal cap; interest can push balance above cap (allowed).
   *
   * <p>Investment accounts (PEA, PEA-PME): capacity used = cumulative deposits → withdrawals do not
   * free up space (pre-5-year rule; Phase B will handle post-5-year withdrawals).
   */
  private static Money computeDepositTotal(
      FinancialAccount account, List<FinancialAccount> allUserAccounts, AccountSubType subType) {

    // Savings: use current balance as the deposit ceiling
    if (isSavingsType(subType)) {
      return account.balance();
    }

    // PEA-PME uses combined PEA+PEA-PME cumulative deposits against the 225k combined limit
    if (subType == AccountSubType.PEA_PME) {
      return allUserAccounts.stream()
          .filter(a -> a.subType() == AccountSubType.PEA || a.subType() == AccountSubType.PEA_PME)
          .map(AccountBusinessRules::sumDeposits)
          .reduce(
              new Money(BigDecimal.ZERO, Currency.getInstance("EUR")),
              AccountBusinessRules::addMoney);
    }

    return sumDeposits(account);
  }

  private static boolean isSavingsType(AccountSubType subType) {
    return subType == AccountSubType.LIVRET_A
        || subType == AccountSubType.LDDS
        || subType == AccountSubType.LDD
        || subType == AccountSubType.LEP
        || subType == AccountSubType.LIVRET_JEUNE;
  }

  private static Money sumDeposits(FinancialAccount account) {
    return account.transactions().stream()
        .filter(t -> t.type() == TransactionType.DEPOSIT)
        .map(Transaction::totalAmount)
        .reduce(
            new Money(BigDecimal.ZERO, Currency.getInstance("EUR")),
            AccountBusinessRules::addMoney);
  }

  private static Money addMoney(Money a, Money b) {
    return new Money(a.amount().add(b.amount()), a.currency());
  }
}
