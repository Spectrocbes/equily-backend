package com.equily.portfolio.domain.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface FxRatePort {
  /**
   * Returns the exchange rate from baseCurrency to targetCurrency. Returns 1.0 if base == target.
   * Returns empty if rate unavailable.
   */
  Optional<BigDecimal> getRate(String baseCurrency, String targetCurrency);

  /**
   * Historical rate for a given date: 1 unit of currency → EUR. Falls back to current rate if
   * historical data is unavailable. Used when recording transactions to persist the EUR equivalent.
   */
  Optional<BigDecimal> getRateToEur(String currency, LocalDate date);
}
