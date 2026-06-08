package com.equily.marketdata.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.equily.marketdata.infrastructure.alphavantage.AlphaVantageAdapter;
import com.equily.marketdata.infrastructure.coingecko.CoinGeckoAdapter;
import com.equily.marketdata.infrastructure.fmp.FmpAdapter;
import com.equily.marketdata.infrastructure.yahoo.YahooFinanceAdapter;
import com.equily.portfolio.domain.marketdata.Quote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompositeMarketDataAdapterTest {

  @Mock private CoinGeckoAdapter coinGecko;
  @Mock private YahooFinanceAdapter yahoo;
  @Mock private FmpAdapter fmp;
  @Mock private AlphaVantageAdapter alphaVantage;

  private CompositeMarketDataAdapter composite;

  @BeforeEach
  void setUp() {
    composite = new CompositeMarketDataAdapter(coinGecko, yahoo, fmp, alphaVantage);
  }

  private Quote quote(String symbol, String price) {
    return new Quote(symbol, new BigDecimal(price), "EUR", symbol, Instant.now(), null);
  }

  @Test
  void routes_crypto_to_coingecko_first() {
    Quote btcQuote = quote("BTC", "42000");
    when(coinGecko.getQuote("BTC")).thenReturn(Optional.of(btcQuote));

    Optional<Quote> result = composite.getQuote("BTC");

    assertThat(result).contains(btcQuote);
  }

  @Test
  void falls_back_to_yahoo_for_crypto_when_coingecko_empty() {
    Quote yahooQuote = quote("BTC", "42000");
    when(coinGecko.getQuote("BTC")).thenReturn(Optional.empty());
    when(yahoo.getQuote("BTC-USD")).thenReturn(Optional.of(yahooQuote));

    Optional<Quote> result = composite.getQuote("BTC");

    assertThat(result).contains(yahooQuote);
  }

  @Test
  void routes_eu_tickers_to_yahoo_first() {
    Quote yahooQuote = quote("CW8.PA", "400");
    when(yahoo.getQuote("CW8.PA")).thenReturn(Optional.of(yahooQuote));

    Optional<Quote> result = composite.getQuote("CW8.PA");

    assertThat(result).contains(yahooQuote);
  }

  @Test
  void falls_back_to_alphavantage_for_eu_when_yahoo_empty() {
    Quote avQuote = quote("CW8.PA", "400");
    when(yahoo.getQuote("CW8.PA")).thenReturn(Optional.empty());
    when(alphaVantage.getQuote("CW8.PA")).thenReturn(Optional.of(avQuote));

    Optional<Quote> result = composite.getQuote("CW8.PA");

    assertThat(result).contains(avQuote);
  }

  @Test
  void routes_us_tickers_to_yahoo_first() {
    Quote yahooQuote = quote("AAPL", "150");
    when(yahoo.getQuote("AAPL")).thenReturn(Optional.of(yahooQuote));

    Optional<Quote> result = composite.getQuote("AAPL");

    assertThat(result).contains(yahooQuote);
  }

  @Test
  void falls_back_to_fmp_for_us_when_yahoo_empty() {
    Quote fmpQuote = quote("AAPL", "150");
    when(yahoo.getQuote("AAPL")).thenReturn(Optional.empty());
    when(fmp.getQuote("AAPL")).thenReturn(Optional.of(fmpQuote));

    Optional<Quote> result = composite.getQuote("AAPL");

    assertThat(result).contains(fmpQuote);
  }

  @Test
  void getQuotes_batches_by_provider() {
    Quote btcQuote = quote("BTC", "42000");
    Quote cwQuote = quote("CW8.PA", "400");
    Quote aaplQuote = quote("AAPL", "150");

    when(coinGecko.getQuotes(List.of("BTC"))).thenReturn(Map.of("BTC", btcQuote));
    when(yahoo.getQuotes(List.of("CW8.PA"))).thenReturn(Map.of("CW8.PA", cwQuote));
    when(yahoo.getQuotes(List.of("AAPL"))).thenReturn(Map.of("AAPL", aaplQuote));
    when(alphaVantage.getQuotes(List.of())).thenReturn(Map.of());
    when(fmp.getQuotes(List.of())).thenReturn(Map.of());

    Map<String, Quote> result = composite.getQuotes(List.of("BTC", "CW8.PA", "AAPL"));

    assertThat(result).containsKeys("BTC", "CW8.PA", "AAPL");
    assertThat(result.get("BTC").price()).isEqualByComparingTo("42000");
    assertThat(result.get("CW8.PA").price()).isEqualByComparingTo("400");
    assertThat(result.get("AAPL").price()).isEqualByComparingTo("150");
  }

  @Test
  void getQuotes_uses_crypto_yahoo_fallback_for_missing_cryptos() {
    Quote yahooFallback = quote("ETH", "2500");
    when(coinGecko.getQuotes(List.of("ETH"))).thenReturn(Map.of());
    when(yahoo.getQuote("ETH-USD")).thenReturn(Optional.of(yahooFallback));
    when(yahoo.getQuotes(List.of())).thenReturn(Map.of());
    when(alphaVantage.getQuotes(List.of())).thenReturn(Map.of());
    when(fmp.getQuotes(List.of())).thenReturn(Map.of());

    Map<String, Quote> result = composite.getQuotes(List.of("ETH"));

    assertThat(result).containsKey("ETH");
  }
}
