package com.equily.marketdata.infrastructure.alphavantage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.equily.portfolio.domain.marketdata.Quote;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class AlphaVantageAdapterTest {

  @Mock private RestClient.Builder builder;
  @Mock private RestClient restClient;
  @Mock private RestClient.RequestHeadersUriSpec requestSpec;
  @Mock private RestClient.ResponseSpec responseSpec;

  private AlphaVantageAdapter adapter;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    when(builder.build()).thenReturn(restClient);
    adapter = new AlphaVantageAdapter(builder, objectMapper, "test-api-key");
  }

  private void mockResponse(String body) {
    when(restClient.get()).thenReturn(requestSpec);
    when(requestSpec.uri(anyString())).thenReturn(requestSpec);
    when(requestSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(String.class)).thenReturn(body);
  }

  private String validAvJson(String price, String changePercent) {
    return """
           {"Global Quote":{
             "01. symbol":"CW8.PA",
             "05. price":"%s",
             "10. change percent":"%s%%"
           }}
           """
        .formatted(price, changePercent);
  }

  @Test
  void getQuote_returns_quote_for_valid_av_response() {
    mockResponse(validAvJson("400.50", "1.23"));

    Optional<Quote> result = adapter.getQuote("CW8.PA");

    assertThat(result).isPresent();
    assertThat(result.get().symbol()).isEqualTo("CW8.PA");
    assertThat(result.get().price()).isEqualByComparingTo("400.50");
    assertThat(result.get().currency()).isEqualTo("EUR");
  }

  @Test
  void getQuote_detects_eur_currency_for_pa_suffix() {
    mockResponse(validAvJson("100", "0"));

    Optional<Quote> result = adapter.getQuote("AIR.PA");

    assertThat(result).isPresent();
    assertThat(result.get().currency()).isEqualTo("EUR");
  }

  @Test
  void getQuote_detects_eur_currency_for_as_suffix() {
    mockResponse(validAvJson("100", "0"));

    Optional<Quote> result = adapter.getQuote("ASML.AS");

    assertThat(result).isPresent();
    assertThat(result.get().currency()).isEqualTo("EUR");
  }

  @Test
  void getQuote_detects_usd_currency_for_us_ticker() {
    mockResponse(validAvJson("150", "0"));

    Optional<Quote> result = adapter.getQuote("AAPL");

    assertThat(result).isPresent();
    assertThat(result.get().currency()).isEqualTo("USD");
  }

  @Test
  void getQuote_returns_empty_when_global_quote_is_empty() {
    mockResponse("{\"Global Quote\":{}}");

    Optional<Quote> result = adapter.getQuote("CW8.PA");

    assertThat(result).isEmpty();
  }

  @Test
  void getQuote_returns_empty_when_global_quote_is_missing() {
    mockResponse("{}");

    Optional<Quote> result = adapter.getQuote("CW8.PA");

    assertThat(result).isEmpty();
  }

  @Test
  void getQuote_returns_empty_after_daily_limit_reached() {
    // Exhaust the limit by calling resetDailyCount and then calling 20 times
    mockResponse(validAvJson("100", "0"));
    for (int i = 0; i < 20; i++) {
      adapter.getQuote("CW8.PA");
    }

    // 21st call should be blocked by the limit
    Optional<Quote> result = adapter.getQuote("CW8.PA");

    assertThat(result).isEmpty();
  }

  @Test
  void resetDailyCount_resets_counter_to_zero() {
    mockResponse(validAvJson("100", "0"));
    for (int i = 0; i < 20; i++) {
      adapter.getQuote("CW8.PA");
    }
    // Limit reached
    assertThat(adapter.getQuote("CW8.PA")).isEmpty();

    // Reset
    adapter.resetDailyCount();

    // Should work again
    assertThat(adapter.getQuote("CW8.PA")).isPresent();
  }

  @Test
  void getQuote_returns_empty_when_restclient_throws() {
    when(restClient.get()).thenThrow(new RuntimeException("timeout"));

    Optional<Quote> result = adapter.getQuote("CW8.PA");

    assertThat(result).isEmpty();
  }
}
