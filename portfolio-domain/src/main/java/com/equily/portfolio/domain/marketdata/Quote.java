package com.equily.portfolio.domain.marketdata;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record Quote(
    String symbol,
    BigDecimal price,
    String currency,
    String name,
    Instant timestamp,
    BigDecimal changePercent) {

  public Quote {
    Objects.requireNonNull(symbol, "symbol must not be null");
    Objects.requireNonNull(price, "price must not be null");
    Objects.requireNonNull(currency, "currency must not be null");
    if (price.compareTo(BigDecimal.ZERO) < 0)
      throw new IllegalArgumentException("price must not be negative");
  }

  public boolean isStale(Duration maxAge) {
    return timestamp != null && Instant.now().isAfter(timestamp.plus(maxAge));
  }
}
