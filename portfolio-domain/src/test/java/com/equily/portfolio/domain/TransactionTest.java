package com.equily.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.equily.portfolio.domain.exception.InvalidTransactionException;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class TransactionTest {

  private static final Currency EUR = Currency.getInstance("EUR");
  private static final Ticker AAPL = new Ticker("AAPL");
  private static final LocalDate TODAY = LocalDate.of(2026, 5, 22);

  @Test
  void buy_with_valid_fields_is_created() {
    Transaction t =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            AAPL,
            new BigDecimal("10"),
            new Money(new BigDecimal("150.00"), EUR),
            new Money(new BigDecimal("1500.00"), EUR),
            TODAY,
            null,
            null);
    assertThat(t.type()).isEqualTo(TransactionType.BUY);
    assertThat(t.ticker()).isEqualTo(AAPL);
    assertThat(t.quantity()).isEqualByComparingTo(new BigDecimal("10"));
  }

  @Test
  void ofEur_sets_eur_defaults() {
    Money total = new Money(new BigDecimal("1500.00"), EUR);
    Transaction t =
        Transaction.ofEur(
            TransactionId.generate(),
            TransactionType.BUY,
            AAPL,
            new BigDecimal("10"),
            new Money(new BigDecimal("150.00"), EUR),
            total,
            TODAY,
            null,
            null);
    assertThat(t.currency()).isEqualTo("EUR");
    assertThat(t.eurFxRate()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(t.amountEur()).isEqualByComparingTo(new BigDecimal("1500.00"));
  }

  @Test
  void of_stores_currency_fxRate_and_amountEur() {
    BigDecimal fxRate = new BigDecimal("0.920000");
    BigDecimal totalUsd = new BigDecimal("1000.00");
    BigDecimal expectedEur = new BigDecimal("920.0000");
    Transaction t =
        Transaction.of(
            TransactionId.generate(),
            TransactionType.BUY,
            AAPL,
            new BigDecimal("10"),
            new Money(new BigDecimal("100.00"), Currency.getInstance("USD")),
            new Money(totalUsd, Currency.getInstance("USD")),
            TODAY,
            BigDecimal.ZERO,
            null,
            "USD",
            expectedEur,
            fxRate,
            null,
            null);
    assertThat(t.currency()).isEqualTo("USD");
    assertThat(t.eurFxRate()).isEqualByComparingTo(fxRate);
    assertThat(t.amountEur()).isEqualByComparingTo(expectedEur);
  }

  @Test
  void of_with_null_currency_throws() {
    assertThatThrownBy(
            () ->
                Transaction.of(
                    TransactionId.generate(),
                    TransactionType.DEPOSIT,
                    null,
                    null,
                    null,
                    new Money(new BigDecimal("1000.00"), EUR),
                    TODAY,
                    null,
                    null,
                    null,
                    new BigDecimal("1000.00"),
                    BigDecimal.ONE,
                    null,
                    null))
        .isInstanceOf(InvalidTransactionException.class);
  }

  @Test
  void of_with_negative_amountEur_throws() {
    assertThatThrownBy(
            () ->
                Transaction.of(
                    TransactionId.generate(),
                    TransactionType.DEPOSIT,
                    null,
                    null,
                    null,
                    new Money(new BigDecimal("1000.00"), EUR),
                    TODAY,
                    null,
                    null,
                    "EUR",
                    new BigDecimal("-1"),
                    BigDecimal.ONE,
                    null,
                    null))
        .isInstanceOf(InvalidTransactionException.class);
  }

  @Test
  void of_with_zero_eurFxRate_throws() {
    assertThatThrownBy(
            () ->
                Transaction.of(
                    TransactionId.generate(),
                    TransactionType.DEPOSIT,
                    null,
                    null,
                    null,
                    new Money(new BigDecimal("1000.00"), EUR),
                    TODAY,
                    null,
                    null,
                    "EUR",
                    new BigDecimal("1000.00"),
                    BigDecimal.ZERO,
                    null,
                    null))
        .isInstanceOf(InvalidTransactionException.class);
  }

  @Test
  void buy_with_null_ticker_throws() {
    assertThatThrownBy(
            () ->
                Transaction.ofEur(
                    TransactionId.generate(),
                    TransactionType.BUY,
                    null,
                    new BigDecimal("10"),
                    new Money(new BigDecimal("150.00"), EUR),
                    new Money(new BigDecimal("1500.00"), EUR),
                    TODAY,
                    null,
                    null))
        .isInstanceOf(InvalidTransactionException.class);
  }

  @Test
  void buy_with_zero_quantity_throws() {
    assertThatThrownBy(
            () ->
                Transaction.ofEur(
                    TransactionId.generate(),
                    TransactionType.BUY,
                    AAPL,
                    BigDecimal.ZERO,
                    new Money(new BigDecimal("150.00"), EUR),
                    new Money(new BigDecimal("1500.00"), EUR),
                    TODAY,
                    null,
                    null))
        .isInstanceOf(InvalidTransactionException.class);
  }

  @Test
  void buy_with_negative_quantity_throws() {
    assertThatThrownBy(
            () ->
                Transaction.ofEur(
                    TransactionId.generate(),
                    TransactionType.BUY,
                    AAPL,
                    new BigDecimal("-5"),
                    new Money(new BigDecimal("150.00"), EUR),
                    new Money(new BigDecimal("750.00"), EUR),
                    TODAY,
                    null,
                    null))
        .isInstanceOf(InvalidTransactionException.class);
  }

  @Test
  void deposit_with_null_totalAmount_throws() {
    assertThatThrownBy(
            () ->
                Transaction.ofEur(
                    TransactionId.generate(),
                    TransactionType.DEPOSIT,
                    null,
                    null,
                    null,
                    null,
                    TODAY,
                    null,
                    null))
        .isInstanceOf(InvalidTransactionException.class);
  }

  @Test
  void deposit_with_non_null_quantity_throws() {
    assertThatThrownBy(
            () ->
                Transaction.ofEur(
                    TransactionId.generate(),
                    TransactionType.DEPOSIT,
                    null,
                    new BigDecimal("1"),
                    null,
                    new Money(new BigDecimal("1000.00"), EUR),
                    TODAY,
                    null,
                    null))
        .isInstanceOf(InvalidTransactionException.class);
  }
}
