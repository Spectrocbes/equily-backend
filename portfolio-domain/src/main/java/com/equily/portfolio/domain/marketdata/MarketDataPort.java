package com.equily.portfolio.domain.marketdata;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MarketDataPort {
  Optional<Quote> getQuote(String symbol);

  Map<String, Quote> getQuotes(List<String> symbols);
}
