package com.equily.portfolio.domain.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuoteTest {

  @Test
  void constructor_accepts_valid_args() {
    Quote quote =
        new Quote("AAPL", new BigDecimal("150.00"), "USD", "Apple Inc.", Instant.now(), null);
    assertThat(quote.symbol()).isEqualTo("AAPL");
    assertThat(quote.price()).isEqualByComparingTo("150.00");
    assertThat(quote.currency()).isEqualTo("USD");
    assertThat(quote.changePercent()).isNull();
  }

  @Test
  void constructor_rejects_null_symbol() {
    assertThatThrownBy(
            () -> new Quote(null, new BigDecimal("150"), "USD", "Apple", Instant.now(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("symbol");
  }

  @Test
  void constructor_rejects_null_price() {
    assertThatThrownBy(() -> new Quote("AAPL", null, "USD", "Apple", Instant.now(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("price");
  }

  @Test
  void constructor_rejects_null_currency() {
    assertThatThrownBy(
            () -> new Quote("AAPL", new BigDecimal("150"), null, "Apple", Instant.now(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("currency");
  }

  @Test
  void constructor_rejects_negative_price() {
    assertThatThrownBy(
            () -> new Quote("AAPL", new BigDecimal("-1"), "USD", "Apple", Instant.now(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("negative");
  }

  @Test
  void isStale_returns_true_for_old_timestamp() {
    Instant fiveMinutesAgo = Instant.now().minusSeconds(310);
    Quote quote = new Quote("AAPL", new BigDecimal("150"), "USD", "Apple", fiveMinutesAgo, null);
    assertThat(quote.isStale(Duration.ofMinutes(5))).isTrue();
  }

  @Test
  void isStale_returns_false_for_recent_timestamp() {
    Quote quote = new Quote("AAPL", new BigDecimal("150"), "USD", "Apple", Instant.now(), null);
    assertThat(quote.isStale(Duration.ofMinutes(5))).isFalse();
  }

  @Test
  void isStale_returns_false_when_timestamp_is_null() {
    Quote quote = new Quote("AAPL", new BigDecimal("150"), "USD", "Apple", null, null);
    assertThat(quote.isStale(Duration.ofMinutes(5))).isFalse();
  }
}
