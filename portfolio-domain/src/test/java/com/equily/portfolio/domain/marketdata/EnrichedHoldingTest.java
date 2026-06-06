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
    return new Quote("AAPL", new BigDecimal(price), "EUR", "Apple Inc.", Instant.now(), null);
  }

  @Test
  void withPrice_computes_marketValue_as_price_times_quantity() {
    Holding holding = buildHolding("10", "100");
    Quote quote = buildQuote("160");

    EnrichedHolding enriched = EnrichedHolding.withPrice(holding, quote);

    assertThat(enriched.marketValue()).isEqualByComparingTo("1600.00");
    assertThat(enriched.priceAvailable()).isTrue();
  }

  @Test
  void withPrice_computes_unrealizedPnl_as_marketValue_minus_totalInvested() {
    Holding holding = buildHolding("10", "100");
    Quote quote = buildQuote("160");

    EnrichedHolding enriched = EnrichedHolding.withPrice(holding, quote);

    // marketValue=1600, totalInvested=1000 => pnl=600
    assertThat(enriched.unrealizedPnl()).isEqualByComparingTo("600.00");
  }

  @Test
  void withPrice_computes_unrealizedPnlPct_correctly() {
    Holding holding = buildHolding("10", "100");
    Quote quote = buildQuote("160");

    EnrichedHolding enriched = EnrichedHolding.withPrice(holding, quote);

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

    EnrichedHolding enriched = EnrichedHolding.withPrice(holding, quote);

    assertThat(enriched.unrealizedPnlPct()).isEqualByComparingTo("0");
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
    assertThat(enriched.holding()).isEqualTo(holding);
  }
}
