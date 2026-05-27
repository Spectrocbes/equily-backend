package com.equily.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.equily.portfolio.domain.exception.InvalidHoldingException;
import com.equily.portfolio.domain.exception.InvalidTransactionException;
import com.equily.shared.Country;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HoldingTest {

  private static final Currency EUR = Currency.getInstance("EUR");
  private static final Ticker AAPL = new Ticker("AAPL");
  private static final AssetType STOCK = AssetType.STOCK;
  private static final AssetMetadata META =
      new AssetMetadata("Apple Inc.", "US0378331005", new Country("US"));
  private static final LocalDate TODAY = LocalDate.of(2026, 5, 22);

  private Transaction buy(String qty, String price) {
    BigDecimal q = new BigDecimal(qty);
    BigDecimal p = new BigDecimal(price);
    return Transaction.of(
        TransactionId.generate(),
        TransactionType.BUY,
        AAPL,
        q,
        new Money(p, EUR),
        new Money(q.multiply(p), EUR),
        TODAY,
        null,
        null);
  }

  private Transaction sell(String qty, String price) {
    BigDecimal q = new BigDecimal(qty);
    BigDecimal p = new BigDecimal(price);
    return Transaction.of(
        TransactionId.generate(),
        TransactionType.SELL,
        AAPL,
        q,
        new Money(p, EUR),
        new Money(q.multiply(p), EUR),
        TODAY,
        null,
        null);
  }

  @Test
  void two_buy_transactions_compute_correct_weighted_average_cost() {
    // Buy 10 @ 100 + Buy 10 @ 200 → avg = 150
    List<Transaction> txns = List.of(buy("10", "100.00"), buy("10", "200.00"));
    Optional<Holding> result = Holding.computeFrom(txns, STOCK, META);

    assertThat(result).isPresent();
    Holding h = result.get();
    assertThat(h.quantity()).isEqualByComparingTo(new BigDecimal("20"));
    assertThat(h.averageCostPrice().amount()).isEqualByComparingTo(new BigDecimal("150.00"));
  }

  @Test
  void buy_then_sell_reduces_quantity_and_preserves_average_cost() {
    // Buy 10 @ 100 → avg = 100; Sell 4 → avg still 100, qty = 6
    List<Transaction> txns = List.of(buy("10", "100.00"), sell("4", "120.00"));
    Optional<Holding> result = Holding.computeFrom(txns, STOCK, META);

    assertThat(result).isPresent();
    Holding h = result.get();
    assertThat(h.quantity()).isEqualByComparingTo(new BigDecimal("6"));
    assertThat(h.averageCostPrice().amount()).isEqualByComparingTo(new BigDecimal("100.00"));
  }

  @Test
  void selling_more_than_held_throws_InvalidHoldingException() {
    List<Transaction> txns = List.of(buy("5", "100.00"), sell("10", "120.00"));
    assertThatThrownBy(() -> Holding.computeFrom(txns, STOCK, META))
        .isInstanceOf(InvalidHoldingException.class);
  }

  @Test
  void all_shares_sold_returns_empty_optional() {
    List<Transaction> txns = List.of(buy("10", "100.00"), sell("10", "150.00"));
    Optional<Holding> result = Holding.computeFrom(txns, STOCK, META);
    assertThat(result).isEmpty();
  }

  @Test
  void buy_with_fees_includes_fees_in_average_cost() {
    // Buy 10 @ 100 + 5€ fees → costBasis = 1005, avgCost = 1005 / 10 = 100.50
    List<Transaction> txns =
        List.of(
            Transaction.of(
                TransactionId.generate(),
                TransactionType.BUY,
                AAPL,
                new BigDecimal("10"),
                new Money(new BigDecimal("100.00"), EUR),
                new Money(new BigDecimal("1005.00"), EUR),
                TODAY,
                new BigDecimal("5.00"),
                null));
    Optional<Holding> result = Holding.computeFrom(txns, STOCK, META);
    assertThat(result).isPresent();
    assertThat(result.get().averageCostPrice().amount())
        .isEqualByComparingTo(new BigDecimal("100.50"));
  }

  @Test
  void negative_fees_throws_InvalidTransactionException() {
    assertThatThrownBy(
            () ->
                Transaction.of(
                    TransactionId.generate(),
                    TransactionType.BUY,
                    AAPL,
                    new BigDecimal("10"),
                    new Money(new BigDecimal("100.00"), EUR),
                    new Money(new BigDecimal("1000.00"), EUR),
                    TODAY,
                    new BigDecimal("-1.00"),
                    null))
        .isInstanceOf(InvalidTransactionException.class);
  }
}
