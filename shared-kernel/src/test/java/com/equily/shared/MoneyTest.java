package com.equily.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.equily.shared.exception.CurrencyMismatchException;
import com.equily.shared.exception.InvalidMoneyException;
import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

  private static final Currency EUR = Currency.getInstance("EUR");
  private static final Currency USD = Currency.getInstance("USD");

  // --- Construction ---

  @Test
  void nullAmount_throwsInvalidMoneyException_notNPE() {
    assertThatThrownBy(() -> new Money(null, EUR))
        .isInstanceOf(InvalidMoneyException.class)
        .isNotInstanceOf(NullPointerException.class);
  }

  @Test
  void nullCurrency_throwsInvalidMoneyException_notNPE() {
    assertThatThrownBy(() -> new Money(BigDecimal.ONE, null))
        .isInstanceOf(InvalidMoneyException.class)
        .isNotInstanceOf(NullPointerException.class);
  }

  @Test
  void construction_normalizesTrailingZeros() {
    Money withZeros = new Money(new BigDecimal("1.00"), EUR);
    Money withoutZeros = new Money(BigDecimal.ONE, EUR);
    assertThat(withZeros).isEqualTo(withoutZeros);
  }

  // --- Add ---

  @Test
  void add_sameCurrency_returnsSum() {
    Money a = new Money(new BigDecimal("10.50"), EUR);
    Money b = new Money(new BigDecimal("4.50"), EUR);
    assertThat(a.add(b)).isEqualTo(new Money(new BigDecimal("15.00"), EUR));
  }

  @Test
  void add_differentCurrency_throwsCurrencyMismatchException() {
    Money eur = new Money(BigDecimal.ONE, EUR);
    Money usd = new Money(BigDecimal.ONE, USD);
    assertThatThrownBy(() -> eur.add(usd)).isInstanceOf(CurrencyMismatchException.class);
  }

  @Test
  void add_nullOther_throwsInvalidMoneyException() {
    Money money = new Money(BigDecimal.ONE, EUR);
    assertThatThrownBy(() -> money.add(null)).isInstanceOf(InvalidMoneyException.class);
  }

  // --- Subtract ---

  @Test
  void subtract_sameCurrency_returnsDifference() {
    Money a = new Money(new BigDecimal("10.00"), EUR);
    Money b = new Money(new BigDecimal("3.25"), EUR);
    assertThat(a.subtract(b)).isEqualTo(new Money(new BigDecimal("6.75"), EUR));
  }

  @Test
  void subtract_differentCurrency_throwsCurrencyMismatchException() {
    Money eur = new Money(BigDecimal.ONE, EUR);
    Money usd = new Money(BigDecimal.ONE, USD);
    assertThatThrownBy(() -> eur.subtract(usd)).isInstanceOf(CurrencyMismatchException.class);
  }

  @Test
  void subtract_nullOther_throwsInvalidMoneyException() {
    Money money = new Money(BigDecimal.ONE, EUR);
    assertThatThrownBy(() -> money.subtract(null)).isInstanceOf(InvalidMoneyException.class);
  }

  // --- Multiply ---

  @Test
  void multiply_scalesResultToTwoDecimalPlaces() {
    Money money = new Money(new BigDecimal("10.00"), EUR);
    Money result = money.multiply(new BigDecimal("1.5"));
    assertThat(result).isEqualTo(new Money(new BigDecimal("15.00"), EUR));
  }

  @Test
  void multiply_usesHalfEvenRounding() {
    // 2.005 → digit dropped is 5 with even preceding digit 0 → round down to 2.00
    Money money1 = new Money(new BigDecimal("2.005"), EUR);
    assertThat(money1.multiply(BigDecimal.ONE).amount())
        .isEqualByComparingTo(new BigDecimal("2.00"));

    // 2.015 → digit dropped is 5 with odd preceding digit 1 → round up to 2.02
    Money money2 = new Money(new BigDecimal("2.015"), EUR);
    assertThat(money2.multiply(BigDecimal.ONE).amount())
        .isEqualByComparingTo(new BigDecimal("2.02"));
  }

  @Test
  void multiply_nullFactor_throwsInvalidMoneyException() {
    Money money = new Money(BigDecimal.ONE, EUR);
    assertThatThrownBy(() -> money.multiply(null)).isInstanceOf(InvalidMoneyException.class);
  }

  // --- Equals & hashCode ---

  @Test
  void equals_sameValueDifferentScale_isTrue() {
    Money a = new Money(new BigDecimal("1.00"), EUR);
    Money b = new Money(new BigDecimal("1"), EUR);
    assertThat(a).isEqualTo(b);
  }

  @Test
  void equals_differentCurrency_isFalse() {
    Money eur = new Money(BigDecimal.ONE, EUR);
    Money usd = new Money(BigDecimal.ONE, USD);
    assertThat(eur).isNotEqualTo(usd);
  }

  @Test
  void equals_differentAmount_isFalse() {
    Money a = new Money(new BigDecimal("1.00"), EUR);
    Money b = new Money(new BigDecimal("2.00"), EUR);
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void hashCode_equalMoneys_sameHash() {
    Money a = new Money(new BigDecimal("1.00"), EUR);
    Money b = new Money(new BigDecimal("1"), EUR);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  // --- toString ---

  @Test
  void toString_formatsToTwoDecimalPlaces() {
    Money money = new Money(BigDecimal.ONE, EUR);
    assertThat(money.toString()).isEqualTo("1.00 EUR");
  }

  @Test
  void toString_usesPlainString_noScientificNotation() {
    // 1000000 strips to 1E+6 internally; setScale(2).toPlainString() must render without E notation
    Money money = new Money(new BigDecimal("1000000.00"), EUR);
    assertThat(money.toString()).isEqualTo("1000000.00 EUR");
  }

  // --- Comparisons ---

  @Test
  void isGreaterThan_largerAmount_isTrue() {
    Money a = new Money(new BigDecimal("10.00"), EUR);
    Money b = new Money(new BigDecimal("5.00"), EUR);
    assertThat(a.isGreaterThan(b)).isTrue();
    assertThat(b.isGreaterThan(a)).isFalse();
  }

  @Test
  void isLessThan_smallerAmount_isTrue() {
    Money a = new Money(new BigDecimal("5.00"), EUR);
    Money b = new Money(new BigDecimal("10.00"), EUR);
    assertThat(a.isLessThan(b)).isTrue();
    assertThat(b.isLessThan(a)).isFalse();
  }

  @Test
  void isGreaterThan_differentCurrency_throwsCurrencyMismatchException() {
    Money eur = new Money(BigDecimal.ONE, EUR);
    Money usd = new Money(BigDecimal.ONE, USD);
    assertThatThrownBy(() -> eur.isGreaterThan(usd)).isInstanceOf(CurrencyMismatchException.class);
  }
}
