package com.equily.portfolio.domain.account;

import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.Transaction;
import com.equily.portfolio.domain.TransactionType;
import com.equily.portfolio.domain.exception.AccountCardinalityException;
import com.equily.portfolio.domain.exception.DepositLimitExceededException;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

  private static final Set<AccountSubType> SINGLE_INSTANCE_SUBTYPES =
      Set.of(
          AccountSubType.LIVRET_A,
          AccountSubType.LDDS,
          AccountSubType.LEP,
          AccountSubType.LIVRET_JEUNE,
          AccountSubType.PEA,
          AccountSubType.PEA_PME);

  /**
   * Cardinality limits per French regulation: LIVRET_A, LDDS, LEP, LIVRET_JEUNE, PEA and PEA_PME
   * are each limited to 1 per person. PEA and PEA_PME may coexist (they are different sub-types,
   * each capped at 1 independently). Closed accounts do not count.
   *
   * @throws AccountCardinalityException if the user already holds a non-closed account of the same
   *     restricted sub-type
   */
  public static void validateCardinality(
      AccountSubType newSubType, List<FinancialAccount> existingAccounts) {

    if (newSubType == null || !SINGLE_INSTANCE_SUBTYPES.contains(newSubType)) {
      return;
    }

    long existingCount =
        existingAccounts.stream()
            .filter(a -> a.subType() == newSubType)
            .filter(a -> !a.isClosed())
            .count();

    if (existingCount >= 1) {
      throw new AccountCardinalityException(
          String.format(
              "You already have a %s account. "
                  + "French regulation allows only one %s per person.",
              newSubType, newSubType));
    }
  }

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

  /**
   * Validates deposit limits AFTER a transaction edit has been applied to the in-memory account.
   * The account's balance and transaction list already reflect the post-edit state when this is
   * called — so savings types (balance-based) and investment types (cumulative-deposit-based) both
   * work correctly without extra arithmetic.
   *
   * @throws DepositLimitExceededException if the post-edit state violates the regulatory cap; the
   *     {@code attempted} field carries the excess amount over the limit
   */
  public static void validateDepositAfterEdit(
      FinancialAccount account, List<FinancialAccount> allUserAccounts) {

    AccountSubType subType = account.subType();
    if (subType == null) return;

    Optional<Money> limitOpt = DepositLimits.limitFor(subType);
    if (limitOpt.isEmpty()) return;

    Money limit = limitOpt.get();
    Money used = computeDepositTotal(account, allUserAccounts, subType);

    if (used.amount().compareTo(limit.amount()) > 0) {
      Money excess = new Money(used.amount().subtract(limit.amount()), limit.currency());
      throw new DepositLimitExceededException(subType, limit, used, excess);
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
   * <p>Savings accounts (Livret A, LDDS, LDD, LEP, Livret Jeune): capacity used = current balance.
   *
   * <p>PEA/PEA-PME < 5 years: capacity used = cumulative deposits (no recovery from withdrawals).
   * PEA-PME additionally applies the combined PEA+PEA-PME rule (225 000 € ceiling).
   *
   * <p>PEA/PEA-PME ≥ 5 years: Loi Pacte — transactions replayed chronologically. Each withdrawal
   * on/after the 5-year anniversary reduces the versements counter by the capital portion
   * (approximated as 100% — conservative). time for an accurate ratio.
   */
  private static Money computeDepositTotal(
      FinancialAccount account, List<FinancialAccount> allUserAccounts, AccountSubType subType) {

    if (isSavingsType(subType)) {
      return account.balance();
    }

    if (!isPeaOlderThan5Years(account)) {
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

    // ≥ 5 years: exact Loi Pacte replay using stored liquidation values
    return new Money(computeAdjustedTotalDeposits(account), Currency.getInstance("EUR"));
  }

  public static boolean isPeaOlderThan5Years(FinancialAccount account) {
    if (account.openedAt() == null) return false;
    return account.openedAt().plusYears(5).isBefore(LocalDate.now().plusDays(1));
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

  /**
   * Computes adjusted total deposits for a PEA account by replaying all WITHDRAWAL transactions
   * that carry stored {@code liquidationValueAtWithdrawal} and {@code grossWithdrawalAmount}
   * (recorded via the Loi Pacte partial-withdrawal path).
   *
   * <p>For each such post-anniversary withdrawal: capitalRatio = runningDeposits / liqValue;
   * withdrawnCapital = grossAmount × capitalRatio; runningDeposits -= withdrawnCapital.
   */
  public static BigDecimal computeAdjustedTotalDeposits(FinancialAccount account) {
    LocalDate fiveYearDate =
        account.openedAt() != null ? account.openedAt().plusYears(5) : LocalDate.MIN;

    BigDecimal runningDeposits = BigDecimal.ZERO;

    List<Transaction> sorted =
        account.transactions().stream()
            .filter(
                t ->
                    t.type() == TransactionType.DEPOSIT
                        || (t.type() == TransactionType.WITHDRAWAL
                            && t.liquidationValueAtWithdrawal() != null
                            && t.grossWithdrawalAmount() != null))
            .sorted(Comparator.comparing(Transaction::date))
            .toList();

    for (Transaction tx : sorted) {
      if (tx.type() == TransactionType.DEPOSIT) {
        runningDeposits = runningDeposits.add(tx.amountEur());
      } else if (tx.type() == TransactionType.WITHDRAWAL && !tx.date().isBefore(fiveYearDate)) {
        BigDecimal liqValue = tx.liquidationValueAtWithdrawal();
        if (liqValue.compareTo(BigDecimal.ZERO) > 0) {
          BigDecimal capitalRatio =
              runningDeposits.divide(liqValue, 6, RoundingMode.HALF_EVEN).min(BigDecimal.ONE);
          BigDecimal withdrawnCapital =
              tx.grossWithdrawalAmount().multiply(capitalRatio).setScale(2, RoundingMode.HALF_EVEN);
          runningDeposits = runningDeposits.subtract(withdrawnCapital).max(BigDecimal.ZERO);
        }
      }
    }
    return runningDeposits;
  }
}
