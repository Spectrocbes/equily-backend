package com.equily.marketdata.infrastructure.fmp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.equily.portfolio.domain.marketdata.Quote;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class FmpAdapterTest {

  @Mock private RestClient.Builder builder;
  @Mock private RestClient restClient;
  @Mock private RestClient.RequestHeadersUriSpec requestSpec;
  @Mock private RestClient.ResponseSpec responseSpec;

  private FmpAdapter adapter;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    when(builder.build()).thenReturn(restClient);
    adapter = new FmpAdapter(builder, objectMapper, "test-api-key");
  }

  private void mockResponse(String body) {
    when(restClient.get()).thenReturn(requestSpec);
    when(requestSpec.uri(anyString())).thenReturn(requestSpec);
    when(requestSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(String.class)).thenReturn(body);
  }

  @Test
  void getQuote_returns_empty_for_symbol_with_dot() {
    Optional<Quote> result = adapter.getQuote("CW8.PA");
    assertThat(result).isEmpty();
  }

  @Test
  void getQuote_returns_quote_for_valid_fmp_response() {
    String json =
        """
        [{"symbol":"AAPL","name":"Apple Inc.","price":150.50,"changePercentage":1.23}]
        """;
    mockResponse(json);

    Optional<Quote> result = adapter.getQuote("AAPL");

    assertThat(result).isPresent();
    assertThat(result.get().symbol()).isEqualTo("AAPL");
    assertThat(result.get().price()).isEqualByComparingTo("150.50");
    assertThat(result.get().currency()).isEqualTo("USD");
    assertThat(result.get().name()).isEqualTo("Apple Inc.");
  }

  @Test
  void getQuote_returns_empty_when_price_field_missing() {
    String json =
        """
        [{"symbol":"AAPL","name":"Apple Inc."}]
        """;
    mockResponse(json);

    Optional<Quote> result = adapter.getQuote("AAPL");

    assertThat(result).isEmpty();
  }

  @Test
  void getQuote_returns_empty_when_array_is_empty() {
    mockResponse("[]");

    Optional<Quote> result = adapter.getQuote("AAPL");

    assertThat(result).isEmpty();
  }

  @Test
  void getQuote_returns_empty_when_restclient_throws() {
    when(restClient.get()).thenThrow(new RuntimeException("timeout"));

    Optional<Quote> result = adapter.getQuote("AAPL");

    assertThat(result).isEmpty();
  }

  @Test
  void getQuotes_skips_symbols_with_dot() {
    Map<String, Quote> result = adapter.getQuotes(List.of("CW8.PA", "AIR.PA"));
    assertThat(result).isEmpty();
  }

  @Test
  void getQuotes_returns_quotes_for_us_symbols() {
    String json =
        """
        [{"symbol":"AAPL","name":"Apple Inc.","price":150.50,"changePercentage":1.23}]
        """;
    mockResponse(json);

    Map<String, Quote> result = adapter.getQuotes(List.of("AAPL"));

    assertThat(result).containsKey("AAPL");
  }
}
