package com.equily.portfolio.domain.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.equily.portfolio.domain.AssetMetadata;
import com.equily.portfolio.domain.AssetType;
import com.equily.portfolio.domain.Holding;
import com.equily.portfolio.domain.Ticker;
import com.equily.shared.Country;
import com.equily.shared.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class EnrichedHoldingTest {

  private static final Currency EUR = Currency.getInstance("EUR");

  private Holding buildHolding(String qty, String avgCost) {
    BigDecimal quantity = new BigDecimal(qty);
    Money avgCostPrice = new Money(new BigDecimal(avgCost), EUR);
    Money totalInvested = avgCostPrice.multiply(quantity);
    return new Holding(
        new Ticker("AAPL"),
        AssetType.STOCK,
        new AssetMetadata("Apple Inc.", "US0378331005", new Country("US")),
        quantity,
        avgCostPrice,
        totalInvested,
        new Money(BigDecimal.ZERO, EUR));
  }

  private Quote buildQuote(String price) {
    return new Quote(
        "AAPL", new BigDecimal(price), "EUR", "Apple Inc.", Instant.now(), new BigDecimal("1.23"));
  }

  @Test
  void withPrice_computes_marketValue_as_price_times_quantity() {
    Holding holding = buildHolding("10", "100");
    Quote quote = buildQuote("160");

    EnrichedHolding enriched =
        EnrichedHolding.withPrice(holding, quote, "EUR", BigDecimal.ONE, BigDecimal.ONE);

    assertThat(enriched.marketValue()).isEqualByComparingTo("1600.00");
    assertThat(enriched.priceAvailable()).isTrue();
    assertThat(enriched.dayChangePercent()).isEqualByComparingTo("1.23");
  }

  @Test
  void withPrice_computes_unrealizedPnl_as_marketValue_minus_totalInvested() {
    Holding holding = buildHolding("10", "100");
    Quote quote = buildQuote("160");

    EnrichedHolding enriched =
        EnrichedHolding.withPrice(holding, quote, "EUR", BigDecimal.ONE, BigDecimal.ONE);

    // marketValue=1600, totalInvested=1000 => pnl=600
    assertThat(enriched.unrealizedPnl()).isEqualByComparingTo("600.00");
  }

  @Test
  void withPrice_computes_unrealizedPnlPct_correctly() {
    Holding holding = buildHolding("10", "100");
    Quote quote = buildQuote("160");

    EnrichedHolding enriched =
        EnrichedHolding.withPrice(holding, quote, "EUR", BigDecimal.ONE, BigDecimal.ONE);

    // pnl=600, totalInvested=1000 => 60.00%
    assertThat(enriched.unrealizedPnlPct()).isEqualByComparingTo("60.00");
  }

  @Test
  void withPrice_returns_zero_pnlPct_when_totalInvested_is_zero() {
    // Edge case: holding with zero totalInvested (shouldn't normally happen)
    Holding holding =
        new Holding(
            new Ticker("AAPL"),
            AssetType.STOCK,
            new AssetMetadata("Apple Inc.", null, new Country("US")),
            new BigDecimal("10"),
            new Money(BigDecimal.ZERO, EUR),
            new Money(BigDecimal.ZERO, EUR),
            new Money(BigDecimal.ZERO, EUR));
    Quote quote = buildQuote("150");

    EnrichedHolding enriched =
        EnrichedHolding.withPrice(holding, quote, "EUR", BigDecimal.ONE, BigDecimal.ONE);

    assertThat(enriched.unrealizedPnlPct()).isEqualByComparingTo("0");
  }

  @Test
  void withPrice_applies_costToTarget_to_total_invested() {
    Holding holding = buildHolding("10", "100"); // totalInvested = 1000 EUR
    Quote quote =
        new Quote(
            "AAPL", new BigDecimal("200"), "USD", "Apple Inc.", Instant.now(), BigDecimal.ZERO);
    // liveToTarget = 1 (USD quote stays in USD), costToTarget = 1.10 (EUR→USD)
    EnrichedHolding enriched =
        EnrichedHolding.withPrice(holding, quote, "USD", BigDecimal.ONE, new BigDecimal("1.10"));

    // costInTarget = 1000 * 1.10 = 1100.00, marketValue = 200 * 10 = 2000.00, pnl = 900.00
    assertThat(enriched.marketValue()).isEqualByComparingTo("2000.00");
    assertThat(enriched.unrealizedPnl()).isEqualByComparingTo("900.00");
  }

  @Test
  void withPrice_pnlPct_uses_fx_converted_cost() {
    Holding holding = buildHolding("10", "100"); // totalInvested = 1000 EUR
    Quote quote =
        new Quote(
            "AAPL", new BigDecimal("200"), "USD", "Apple Inc.", Instant.now(), BigDecimal.ZERO);
    EnrichedHolding enriched =
        EnrichedHolding.withPrice(holding, quote, "USD", BigDecimal.ONE, new BigDecimal("1.10"));

    // pnl = 900, costInTarget = 1100 → 900/1100 * 100 = 81.82%
    assertThat(enriched.unrealizedPnlPct()).isEqualByComparingTo("81.82");
  }

  @Test
  void withPrice_applies_liveToTarget_independently_of_costToTarget() {
    Holding holding = buildHolding("5", "200"); // totalInvested = 1000 EUR
    Quote quote =
        new Quote(
            "AAPL", new BigDecimal("300"), "USD", "Apple Inc.", Instant.now(), BigDecimal.ZERO);
    // USD→EUR live rate, cost stays in EUR (costToTarget = 1)
    EnrichedHolding enriched =
        EnrichedHolding.withPrice(holding, quote, "EUR", new BigDecimal("0.90"), BigDecimal.ONE);

    // priceInTarget = 300 * 0.90 = 270.00, marketValue = 270 * 5 = 1350.00
    // costInTarget = 1000 * 1 = 1000.00, pnl = 350.00
    assertThat(enriched.currentPrice()).isEqualByComparingTo("270.00");
    assertThat(enriched.marketValue()).isEqualByComparingTo("1350.00");
    assertThat(enriched.unrealizedPnl()).isEqualByComparingTo("350.00");
  }

  @Test
  void withoutPrice_has_priceAvailable_false_and_all_price_fields_null() {
    Holding holding = buildHolding("10", "100");

    EnrichedHolding enriched = EnrichedHolding.withoutPrice(holding);

    assertThat(enriched.priceAvailable()).isFalse();
    assertThat(enriched.currentPrice()).isNull();
    assertThat(enriched.currency()).isNull();
    assertThat(enriched.marketValue()).isNull();
    assertThat(enriched.unrealizedPnl()).isNull();
    assertThat(enriched.unrealizedPnlPct()).isNull();
    assertThat(enriched.dayChangePercent()).isNull();
    assertThat(enriched.holding()).isEqualTo(holding);
  }

  @Test
  void withPrice_converts_avgCostPrice_to_target_currency() {
    Holding holding = buildHolding("10", "100"); // avgCostPrice=100 EUR
    Quote quote =
        new Quote(
            "AAPL", new BigDecimal("200"), "USD", "Apple Inc.", Instant.now(), BigDecimal.ZERO);

    // costToTarget = 1.10 (EUR→USD)
    EnrichedHolding enriched =
        EnrichedHolding.withPrice(holding, quote, "USD", BigDecimal.ONE, new BigDecimal("1.10"));

    // avgCostInTarget = 100 EUR * 1.10 = 110.00 USD
    assertThat(enriched.avgCostInTarget()).isEqualByComparingTo("110.00");
  }

  @Test
  void withPrice_converts_totalInvested_to_target_currency() {
    Holding holding = buildHolding("10", "100"); // totalInvested = 1000 EUR
    Quote quote =
        new Quote(
            "AAPL", new BigDecimal("200"), "USD", "Apple Inc.", Instant.now(), BigDecimal.ZERO);

    // costToTarget = 1.10 (EUR→USD)
    EnrichedHolding enriched =
        EnrichedHolding.withPrice(holding, quote, "USD", BigDecimal.ONE, new BigDecimal("1.10"));

    // totalInvestedInTarget = 1000 EUR * 1.10 = 1100.00 USD
    assertThat(enriched.totalInvestedInTarget()).isEqualByComparingTo("1100.00");
  }
}
