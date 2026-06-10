package com.equily.marketdata.infrastructure.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.equily.marketdata.infrastructure.yahoo.YahooFinanceAdapter;
import com.equily.portfolio.domain.marketdata.Quote;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YahooFxRateAdapterTest {

  @Mock private YahooFinanceAdapter yahooAdapter;
  @InjectMocks private YahooFxRateAdapter adapter;

  @Test
  void getRate_returns_one_for_same_currency() {
    Optional<BigDecimal> rate = adapter.getRate("EUR", "EUR");

    assertThat(rate).isPresent();
    assertThat(rate.get()).isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void getRate_converts_via_yahoo_ticker_format() {
    Quote quote =
        new Quote(
            "USDEUR=X", new BigDecimal("0.915"), "EUR", "USD/EUR", Instant.now(), BigDecimal.ZERO);
    when(yahooAdapter.getQuote("USDEUR=X")).thenReturn(Optional.of(quote));

    Optional<BigDecimal> rate = adapter.getRate("USD", "EUR");

    assertThat(rate).isPresent();
    assertThat(rate.get()).isEqualByComparingTo("0.915");
  }

  @Test
  void getRate_returns_empty_when_yahoo_fails() {
    when(yahooAdapter.getQuote("GBPUSD=X")).thenReturn(Optional.empty());

    Optional<BigDecimal> rate = adapter.getRate("GBP", "USD");

    assertThat(rate).isEmpty();
  }

  @Test
  void getRateToEur_returns_one_for_eur() {
    Optional<BigDecimal> rate = adapter.getRateToEur("EUR", LocalDate.of(2026, 1, 15));

    assertThat(rate).isPresent();
    assertThat(rate.get()).isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void getRateToEur_returns_historical_rate_from_yahoo() {
    LocalDate date = LocalDate.of(2026, 1, 15);
    when(yahooAdapter.getHistoricalClose("USDEUR=X", date))
        .thenReturn(Optional.of(new BigDecimal("0.921500")));

    Optional<BigDecimal> rate = adapter.getRateToEur("USD", date);

    assertThat(rate).isPresent();
    assertThat(rate.get()).isEqualByComparingTo("0.9215");
  }

  @Test
  void getRateToEur_falls_back_to_current_rate_when_historical_unavailable() {
    LocalDate date = LocalDate.of(2026, 1, 15);
    when(yahooAdapter.getHistoricalClose("GBPEUR=X", date)).thenReturn(Optional.empty());
    Quote fallbackQuote =
        new Quote(
            "GBPEUR=X", new BigDecimal("1.180"), "EUR", "GBP/EUR", Instant.now(), BigDecimal.ZERO);
    when(yahooAdapter.getQuote("GBPEUR=X")).thenReturn(Optional.of(fallbackQuote));

    Optional<BigDecimal> rate = adapter.getRateToEur("GBP", date);

    assertThat(rate).isPresent();
    assertThat(rate.get()).isEqualByComparingTo("1.180");
  }
}
