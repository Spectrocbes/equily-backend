package com.equily.marketdata.infrastructure.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.equily.marketdata.infrastructure.yahoo.YahooFinanceAdapter;
import com.equily.portfolio.domain.marketdata.Quote;
import java.math.BigDecimal;
import java.time.Instant;
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
}
