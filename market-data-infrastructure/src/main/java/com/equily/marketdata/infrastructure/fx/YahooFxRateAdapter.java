package com.equily.marketdata.infrastructure.fx;

import com.equily.marketdata.infrastructure.yahoo.YahooFinanceAdapter;
import com.equily.portfolio.domain.marketdata.FxRatePort;
import com.equily.portfolio.domain.marketdata.Quote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Fetches FX rates from Yahoo Finance using ticker format {BASE}{TARGET}=X (e.g. USDEUR=X). */
@Component
@Primary
public class YahooFxRateAdapter implements FxRatePort {

  private static final Logger log = LoggerFactory.getLogger(YahooFxRateAdapter.class);

  private final YahooFinanceAdapter yahooAdapter;

  public YahooFxRateAdapter(YahooFinanceAdapter yahooAdapter) {
    this.yahooAdapter = yahooAdapter;
  }

  @Override
  @Cacheable(value = "fxRates", key = "#baseCurrency + '_' + #targetCurrency")
  public Optional<BigDecimal> getRate(String baseCurrency, String targetCurrency) {
    if (baseCurrency.equals(targetCurrency)) {
      return Optional.of(BigDecimal.ONE);
    }
    String ticker = baseCurrency + targetCurrency + "=X";
    return yahooAdapter.getQuote(ticker).map(Quote::price);
  }

  @Override
  public Optional<BigDecimal> getRateToEur(String currency, LocalDate date) {
    if ("EUR".equals(currency)) {
      return Optional.of(BigDecimal.ONE);
    }
    String ticker = currency + "EUR=X";
    Optional<BigDecimal> historical = yahooAdapter.getHistoricalClose(ticker, date);
    if (historical.isPresent()) {
      return historical.map(r -> r.setScale(6, RoundingMode.HALF_EVEN));
    }
    log.warn(
        "Historical FX rate unavailable for {} on {}, falling back to current rate",
        currency,
        date);
    return getRate(currency, "EUR");
  }
}
