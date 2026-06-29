package com.equily.portfolio.domain.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MarketDataPort {
  Optional<Quote> getQuote(String symbol);

  Map<String, Quote> getQuotes(List<String> symbols);

  /**
   * Returns daily closing prices for a ticker over a date range. Returns empty map if data
   * unavailable. Key = date, Value = closing price.
   */
  Map<LocalDate, BigDecimal> getHistoricalPrices(String ticker, LocalDate from, LocalDate to);
}
