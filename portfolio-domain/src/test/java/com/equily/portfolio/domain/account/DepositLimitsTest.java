package com.equily.portfolio.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.equily.shared.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DepositLimitsTest {

  private static final Currency EUR = Currency.getInstance("EUR");

  @Test
  void limitFor_livretA_returns_22950() {
    Optional<Money> limit = DepositLimits.limitFor(AccountSubType.LIVRET_A);
    assertThat(limit).isPresent();
    assertThat(limit.get().amount()).isEqualByComparingTo(new BigDecimal("22950.00"));
    assertThat(limit.get().currency()).isEqualTo(EUR);
  }

  @Test
  void limitFor_ldds_returns_12000() {
    Optional<Money> limit = DepositLimits.limitFor(AccountSubType.LDDS);
    assertThat(limit).isPresent();
    assertThat(limit.get().amount()).isEqualByComparingTo(new BigDecimal("12000.00"));
  }

  @Test
  void limitFor_ldd_returns_same_as_ldds() {
    Optional<Money> ldd = DepositLimits.limitFor(AccountSubType.LDD);
    Optional<Money> ldds = DepositLimits.limitFor(AccountSubType.LDDS);
    assertThat(ldd).isPresent();
    assertThat(ldd.get().amount()).isEqualByComparingTo(ldds.get().amount());
  }

  @Test
  void limitFor_pea_returns_150000() {
    Optional<Money> limit = DepositLimits.limitFor(AccountSubType.PEA);
    assertThat(limit).isPresent();
    assertThat(limit.get().amount()).isEqualByComparingTo(new BigDecimal("150000.00"));
  }

  @Test
  void limitFor_pea_pme_returns_225000_combined() {
    Optional<Money> limit = DepositLimits.limitFor(AccountSubType.PEA_PME);
    assertThat(limit).isPresent();
    assertThat(limit.get().amount()).isEqualByComparingTo(new BigDecimal("225000.00"));
  }

  @Test
  void limitFor_cashAccount_returns_empty() {
    Optional<Money> limit = DepositLimits.limitFor(AccountSubType.CASH_ACCOUNT);
    assertThat(limit).isEmpty();
  }

  @Test
  void limitFor_other_returns_empty() {
    Optional<Money> limit = DepositLimits.limitFor(AccountSubType.OTHER);
    assertThat(limit).isEmpty();
  }

  @Test
  void limitFor_compteTitres_returns_empty() {
    Optional<Money> limit = DepositLimits.limitFor(AccountSubType.COMPTE_TITRES);
    assertThat(limit).isEmpty();
  }
}
