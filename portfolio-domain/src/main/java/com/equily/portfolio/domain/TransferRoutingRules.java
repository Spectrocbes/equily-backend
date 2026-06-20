package com.equily.portfolio.domain;

import com.equily.portfolio.domain.exception.TransferRoutingException;

/**
 * Domain service enforcing transfer routing rules between account types. French regulatory
 * constraints: savings and investment accounts cannot transfer directly; funds must transit through
 * a linked checking (CASH_ACCOUNT).
 */
public final class TransferRoutingRules {

  private TransferRoutingRules() {}

  /**
   * Validates that a transfer from → to is permitted.
   *
   * @param from the source account
   * @param to the destination account, or null for an external transfer
   * @throws TransferRoutingException if the transfer is not permitted
   */
  public static void validate(FinancialAccount from, FinancialAccount to) {
    AccountType fromType = from.accountType();
    AccountType toType = to != null ? to.accountType() : null;

    // External transfer: only CASH_ACCOUNT and CRYPTO_WALLET allowed
    if (to == null) {
      if (fromType != AccountType.CASH_ACCOUNT && fromType != AccountType.CRYPTO_WALLET) {
        throw new TransferRoutingException(
            "Only checking accounts and crypto wallets can transfer to external destinations.");
      }
      return;
    }

    // SAVINGS → SAVINGS: forbidden
    if (fromType == AccountType.SAVINGS_ACCOUNT && toType == AccountType.SAVINGS_ACCOUNT) {
      throw new TransferRoutingException(
          "Direct transfer between savings accounts is forbidden. "
              + "Funds must transit via a checking account.");
    }

    // SAVINGS → INVESTMENT: forbidden
    if (fromType == AccountType.SAVINGS_ACCOUNT && isInvestmentType(toType)) {
      throw new TransferRoutingException(
          "Direct transfer from savings to investment is forbidden. "
              + "Funds must transit via a checking account.");
    }

    // INVESTMENT → SAVINGS: forbidden
    if (isInvestmentType(fromType) && toType == AccountType.SAVINGS_ACCOUNT) {
      throw new TransferRoutingException(
          "Direct transfer from investment to savings is forbidden. "
              + "Funds must transit via a checking account.");
    }

    // INVESTMENT → non-linked CASH_ACCOUNT
    if (isInvestmentType(fromType) && toType == AccountType.CASH_ACCOUNT) {
      if (from.linkedCheckingAccountId() == null) {
        throw new TransferRoutingException(
            "This investment account has no linked checking account. "
                + "Please link a checking account first.");
      }
      if (!from.linkedCheckingAccountId().equals(to.id().value())) {
        throw new TransferRoutingException(
            "Investment account can only transfer to its linked checking account. "
                + "Current linked account: "
                + from.linkedCheckingAccountId());
      }
    }

    // CASH_ACCOUNT → INVESTMENT with mismatched or missing link
    if (fromType == AccountType.CASH_ACCOUNT && isInvestmentType(toType)) {
      if (to.linkedCheckingAccountId() == null) {
        throw new TransferRoutingException(
            "Target investment account has no linked checking account. "
                + "Please link a checking account to the investment account first.");
      }
      if (!to.linkedCheckingAccountId().equals(from.id().value())) {
        throw new TransferRoutingException(
            "This checking account is not linked to the target investment account.");
      }
    }
  }

  private static boolean isInvestmentType(AccountType type) {
    return type == AccountType.PEA
        || type == AccountType.PEA_PME
        || type == AccountType.COMPTE_TITRES
        || type == AccountType.PER
        || type == AccountType.ASSURANCE_VIE;
  }
}
