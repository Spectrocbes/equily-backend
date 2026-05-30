package com.equily.portfolio.domain.account;

import com.equily.shared.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;

/**
 * Regulatory deposit limits for French financial accounts.
 *
 * <p>UPDATE THIS CLASS when the government changes the limits. Each constant references the legal
 * article for traceability.
 *
 * <p>Last updated: 2026-01 (no changes since 2023 revalorization)
 */
public final class DepositLimits {

  private DepositLimits() {}

  private static final Currency EUR = Currency.getInstance("EUR");

  // --- Savings limits ---

  /** Livret A — Art. L221-1 CMF. Revalorized 2023. */
  public static final Money LIVRET_A = new Money(new BigDecimal("22950.00"), EUR);

  /** LDDS (ex-Codevi) — Art. L221-27 CMF. */
  public static final Money LDDS = new Money(new BigDecimal("12000.00"), EUR);

  /** LEP — Art. L221-14 CMF. Revalorized 2023. */
  public static final Money LEP = new Money(new BigDecimal("10000.00"), EUR);

  /** Livret Jeune — Art. L221-24 CMF. */
  public static final Money LIVRET_JEUNE = new Money(new BigDecimal("1600.00"), EUR);

  // --- Investment limits ---

  /** PEA — Art. L221-30 CMF. Versements only (gains excluded). */
  public static final Money PEA = new Money(new BigDecimal("150000.00"), EUR);

  /**
   * PEA + PEA-PME combined — Art. L221-32 CMF. The combined total of PEA deposits + PEA-PME
   * deposits cannot exceed this limit.
   */
  public static final Money PEA_PME_COMBINED = new Money(new BigDecimal("225000.00"), EUR);

  /** Warning threshold: show warning when deposits exceed this % of limit. */
  public static final BigDecimal WARNING_THRESHOLD_PERCENT = new BigDecimal("0.90");

  /**
   * Returns the deposit limit for a given AccountSubType. Returns empty if no regulatory limit
   * applies.
   */
  public static Optional<Money> limitFor(AccountSubType subType) {
    return switch (subType) {
      case LIVRET_A -> Optional.of(LIVRET_A);
      case LDDS, LDD -> Optional.of(LDDS);
      case LEP -> Optional.of(LEP);
      case LIVRET_JEUNE -> Optional.of(LIVRET_JEUNE);
      case PEA -> Optional.of(PEA);
      case PEA_PME -> Optional.of(PEA_PME_COMBINED);
      default -> Optional.empty();
    };
  }
}
