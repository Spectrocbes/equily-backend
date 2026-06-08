package com.equily.portfolio.domain.marketdata;

import java.math.BigDecimal;
import java.util.Optional;

public interface FxRatePort {
  /**
   * Returns the exchange rate from baseCurrency to targetCurrency. Returns 1.0 if base == target.
   * Returns empty if rate unavailable.
   */
  Optional<BigDecimal> getRate(String baseCurrency, String targetCurrency);
}
