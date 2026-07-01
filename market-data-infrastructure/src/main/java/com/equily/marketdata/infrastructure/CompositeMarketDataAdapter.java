package com.equily.marketdata.infrastructure;

import com.equily.marketdata.infrastructure.alphavantage.AlphaVantageAdapter;
import com.equily.marketdata.infrastructure.coingecko.CoinGeckoAdapter;
import com.equily.marketdata.infrastructure.fmp.FmpAdapter;
import com.equily.marketdata.infrastructure.yahoo.YahooFinanceAdapter;
import com.equily.portfolio.domain.marketdata.MarketDataPort;
import com.equily.portfolio.domain.marketdata.Quote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class CompositeMarketDataAdapter implements MarketDataPort {

  private static final Logger log = LoggerFactory.getLogger(CompositeMarketDataAdapter.class);

  private static final Set<String> EU_SUFFIXES =
      Set.of(".PA", ".AS", ".DE", ".L", ".MI", ".BR", ".LS", ".MC");

  private final CoinGeckoAdapter coinGecko;
  private final YahooFinanceAdapter yahoo;
  private final FmpAdapter fmp;
  private final AlphaVantageAdapter alphaVantage;

  public CompositeMarketDataAdapter(
      CoinGeckoAdapter coinGecko,
      YahooFinanceAdapter yahoo,
      FmpAdapter fmp,
      AlphaVantageAdapter alphaVantage) {
    this.coinGecko = coinGecko;
    this.yahoo = yahoo;
    this.fmp = fmp;
    this.alphaVantage = alphaVantage;
  }

  @Override
  @Cacheable(value = "quotes", key = "#symbol")
  public Optional<Quote> getQuote(String symbol) {
    if (CryptoSymbols.isCrypto(symbol)) {
      return coinGecko.getQuote(symbol).or(() -> yahoo.getQuote(symbol + "-USD"));
    }

    if (isEuTicker(symbol)) {
      return yahoo.getQuote(symbol).or(() -> alphaVantage.getQuote(symbol));
    }

    return yahoo.getQuote(symbol).or(() -> fmp.getQuote(symbol));
  }

  @Override
  public Map<String, Quote> getQuotes(List<String> symbols) {
    Map<String, Quote> results = new HashMap<>();

    List<String> cryptos = symbols.stream().filter(CryptoSymbols::isCrypto).toList();
    List<String> eu = symbols.stream().filter(this::isEuTicker).toList();
    List<String> us =
        symbols.stream().filter(s -> !CryptoSymbols.isCrypto(s) && !isEuTicker(s)).toList();

    results.putAll(coinGecko.getQuotes(cryptos));
    cryptos.stream()
        .filter(s -> !results.containsKey(s))
        .forEach(s -> yahoo.getQuote(s + "-USD").ifPresent(q -> results.put(s, q)));

    results.putAll(yahoo.getQuotes(eu));
    List<String> euMissing = eu.stream().filter(s -> !results.containsKey(s)).toList();
    results.putAll(alphaVantage.getQuotes(euMissing));

    results.putAll(yahoo.getQuotes(us));
    List<String> usMissing = us.stream().filter(s -> !results.containsKey(s)).toList();
    results.putAll(fmp.getQuotes(usMissing));

    return results;
  }

  @Override
  @Cacheable(value = "historicalPrices", key = "#ticker + '_' + #from + '_' + #to")
  public Map<LocalDate, BigDecimal> getHistoricalPrices(
      String ticker, LocalDate from, LocalDate to) {
    String symbol = CryptoSymbols.isCrypto(ticker) ? ticker + "-USD" : ticker;
    return yahoo.getHistoricalPrices(symbol, from, to);
  }

  private boolean isEuTicker(String symbol) {
    return EU_SUFFIXES.stream().anyMatch(suffix -> symbol.toUpperCase().endsWith(suffix));
  }
}
