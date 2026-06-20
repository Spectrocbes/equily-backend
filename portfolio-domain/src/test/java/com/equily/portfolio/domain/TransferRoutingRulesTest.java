package com.equily.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.account.AccountStatus;
import com.equily.portfolio.domain.account.AccountSubType;
import com.equily.portfolio.domain.exception.TransferRoutingException;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferRoutingRulesTest {

  private static final Currency EUR = Currency.getInstance("EUR");
  private static final LocalDate TODAY = LocalDate.of(2026, 1, 1);

  private FinancialAccount account(AccountType type, AccountSubType subType) {
    return FinancialAccount.reconstruct(
        FinancialAccountId.generate(),
        "Account",
        type,
        new Money(BigDecimal.ZERO, EUR),
        java.util.List.of(),
        "Broker",
        UserId.generate(),
        subType,
        TODAY,
        AccountStatus.ACTIVE,
        null,
        null);
  }

  private FinancialAccount accountWithLink(
      AccountType type, AccountSubType subType, UUID linkedId) {
    FinancialAccount acc = account(type, subType);
    acc.linkCheckingAccount(linkedId);
    return acc;
  }

  // ---- allowed paths ----

  @Test
  void allows_checking_to_savings() {
    FinancialAccount from = account(AccountType.CASH_ACCOUNT, AccountSubType.CASH_ACCOUNT);
    FinancialAccount to = account(AccountType.SAVINGS_ACCOUNT, AccountSubType.LIVRET_A);
    assertThatNoException().isThrownBy(() -> TransferRoutingRules.validate(from, to));
  }

  @Test
  void allows_savings_to_checking() {
    FinancialAccount from = account(AccountType.SAVINGS_ACCOUNT, AccountSubType.LIVRET_A);
    FinancialAccount to = account(AccountType.CASH_ACCOUNT, AccountSubType.CASH_ACCOUNT);
    assertThatNoException().isThrownBy(() -> TransferRoutingRules.validate(from, to));
  }

  @Test
  void allows_checking_to_pea_when_linked() {
    FinancialAccount checking = account(AccountType.CASH_ACCOUNT, AccountSubType.CASH_ACCOUNT);
    FinancialAccount pea =
        accountWithLink(AccountType.PEA, AccountSubType.PEA, checking.id().value());
    assertThatNoException().isThrownBy(() -> TransferRoutingRules.validate(checking, pea));
  }

  @Test
  void allows_pea_to_linked_checking() {
    FinancialAccount checking = account(AccountType.CASH_ACCOUNT, AccountSubType.CASH_ACCOUNT);
    FinancialAccount pea =
        accountWithLink(AccountType.PEA, AccountSubType.PEA, checking.id().value());
    assertThatNoException().isThrownBy(() -> TransferRoutingRules.validate(pea, checking));
  }

  @Test
  void allows_checking_to_external() {
    FinancialAccount checking = account(AccountType.CASH_ACCOUNT, AccountSubType.CASH_ACCOUNT);
    assertThatNoException().isThrownBy(() -> TransferRoutingRules.validate(checking, null));
  }

  @Test
  void allows_crypto_to_external() {
    FinancialAccount crypto = account(AccountType.CRYPTO_WALLET, AccountSubType.CRYPTO_WALLET);
    assertThatNoException().isThrownBy(() -> TransferRoutingRules.validate(crypto, null));
  }

  // ---- forbidden paths ----

  @Test
  void forbids_savings_to_savings() {
    FinancialAccount from = account(AccountType.SAVINGS_ACCOUNT, AccountSubType.LIVRET_A);
    FinancialAccount to = account(AccountType.SAVINGS_ACCOUNT, AccountSubType.LDDS);
    assertThatThrownBy(() -> TransferRoutingRules.validate(from, to))
        .isInstanceOf(TransferRoutingException.class)
        .hasMessageContaining("Direct transfer between savings accounts is forbidden");
  }

  @Test
  void forbids_savings_to_investment() {
    FinancialAccount from = account(AccountType.SAVINGS_ACCOUNT, AccountSubType.LIVRET_A);
    FinancialAccount to = account(AccountType.PEA, AccountSubType.PEA);
    assertThatThrownBy(() -> TransferRoutingRules.validate(from, to))
        .isInstanceOf(TransferRoutingException.class)
        .hasMessageContaining("Direct transfer from savings to investment is forbidden");
  }

  @Test
  void forbids_investment_to_savings() {
    FinancialAccount from = account(AccountType.PEA, AccountSubType.PEA);
    FinancialAccount to = account(AccountType.SAVINGS_ACCOUNT, AccountSubType.LIVRET_A);
    assertThatThrownBy(() -> TransferRoutingRules.validate(from, to))
        .isInstanceOf(TransferRoutingException.class)
        .hasMessageContaining("Direct transfer from investment to savings is forbidden");
  }

  @Test
  void forbids_pea_to_non_linked_checking() {
    FinancialAccount otherChecking = account(AccountType.CASH_ACCOUNT, AccountSubType.CASH_ACCOUNT);
    UUID differentId = UUID.randomUUID();
    FinancialAccount pea = accountWithLink(AccountType.PEA, AccountSubType.PEA, differentId);
    assertThatThrownBy(() -> TransferRoutingRules.validate(pea, otherChecking))
        .isInstanceOf(TransferRoutingException.class)
        .hasMessageContaining("linked checking account");
  }

  @Test
  void forbids_pea_to_checking_when_no_link() {
    FinancialAccount pea = account(AccountType.PEA, AccountSubType.PEA);
    FinancialAccount checking = account(AccountType.CASH_ACCOUNT, AccountSubType.CASH_ACCOUNT);
    assertThatThrownBy(() -> TransferRoutingRules.validate(pea, checking))
        .isInstanceOf(TransferRoutingException.class)
        .hasMessageContaining("no linked checking account");
  }

  @Test
  void forbids_savings_to_external() {
    FinancialAccount savings = account(AccountType.SAVINGS_ACCOUNT, AccountSubType.LIVRET_A);
    assertThatThrownBy(() -> TransferRoutingRules.validate(savings, null))
        .isInstanceOf(TransferRoutingException.class)
        .hasMessageContaining("Only checking accounts and crypto wallets");
  }
}
