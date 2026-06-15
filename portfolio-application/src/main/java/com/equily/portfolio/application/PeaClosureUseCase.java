package com.equily.portfolio.application;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.PeaWithdrawalSimulation;
import java.math.BigDecimal;

/**
 * Input port for PEA closure operations. Implementations live in the same package (application
 * layer).
 */
public interface PeaClosureUseCase {

  /**
   * Simulates the tax impact of a PEA withdrawal or closure. Read-only — does not modify the
   * account.
   *
   * <p>&lt; 5 years: full closure forced — {@code withdrawalAmount} is ignored (effective =
   * liquidationValue).
   *
   * <p>&ge; 5 years: partial or full withdrawal — {@code withdrawalAmount} sets the amount (null =
   * full liquidationValue).
   *
   * @param withdrawalAmount requested withdrawal amount (null → liquidationValue)
   * @param livePortfolioValue live market value of all holdings in EUR
   * @throws com.equily.portfolio.domain.exception.AccountNotFoundException if not found / not owned
   * @throws IllegalArgumentException if account is not a PEA or PEA-PME
   */
  PeaWithdrawalSimulation simulate(
      FinancialAccountId accountId,
      UserId userId,
      BigDecimal withdrawalAmount,
      BigDecimal livePortfolioValue);

  /**
   * Closes a PEA:
   *
   * <ol>
   *   <li>Validates all holdings are sold (none open)
   *   <li>Creates a WITHDRAWAL for the net-of-tax amount received by the user
   *   <li>Creates a WITHDRAWAL for the flat tax (IR + PS) — only if taxes &gt; 0
   *   <li>Marks the account CLOSED
   * </ol>
   *
   * @param livePortfolioValue live market value of all holdings in EUR
   * @throws com.equily.portfolio.application.exception.PeaClosureException if holdings remain open
   * @throws com.equily.portfolio.domain.exception.AccountNotFoundException if not found / not owned
   * @throws IllegalArgumentException if account is not a PEA or PEA-PME
   */
  void closePea(FinancialAccountId accountId, UserId userId, BigDecimal livePortfolioValue);
}
