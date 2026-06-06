package com.equily.marketdata.infrastructure.coingecko;

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
class CoinGeckoAdapterTest {

  @Mock private RestClient.Builder builder;
  @Mock private RestClient restClient;
  @Mock private RestClient.RequestHeadersUriSpec requestSpec;
  @Mock private RestClient.ResponseSpec responseSpec;

  private CoinGeckoAdapter adapter;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    when(builder.build()).thenReturn(restClient);
    adapter = new CoinGeckoAdapter(builder, objectMapper);
  }

  private void mockResponse(String body) {
    when(restClient.get()).thenReturn(requestSpec);
    when(requestSpec.uri(anyString())).thenReturn(requestSpec);
    when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
    when(requestSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(String.class)).thenReturn(body);
  }

  @Test
  void getQuote_returns_quote_for_valid_response() {
    String json =
        """
        {"bitcoin":{"eur":42000.50,"eur_24h_change":1.23}}
        """;
    mockResponse(json);

    Optional<Quote> result = adapter.getQuote("BTC");

    assertThat(result).isPresent();
    assertThat(result.get().symbol()).isEqualTo("BTC");
    assertThat(result.get().price()).isEqualByComparingTo("42000.50");
    assertThat(result.get().currency()).isEqualTo("EUR");
  }

  @Test
  void getQuote_returns_empty_for_unknown_symbol() {
    Optional<Quote> result = adapter.getQuote("AAPL");
    assertThat(result).isEmpty();
  }

  @Test
  void getQuote_returns_empty_when_api_response_is_missing_node() {
    mockResponse("{}");

    Optional<Quote> result = adapter.getQuote("BTC");

    assertThat(result).isEmpty();
  }

  @Test
  void getQuote_returns_empty_when_restclient_throws() {
    when(restClient.get()).thenThrow(new RuntimeException("connection refused"));

    Optional<Quote> result = adapter.getQuote("BTC");

    assertThat(result).isEmpty();
  }

  @Test
  void getQuotes_returns_map_for_multiple_symbols() {
    String json =
        """
        {
          "bitcoin":{"eur":42000.50,"eur_24h_change":1.23},
          "ethereum":{"eur":2500.00,"eur_24h_change":-0.50}
        }
        """;
    mockResponse(json);

    Map<String, Quote> result = adapter.getQuotes(List.of("BTC", "ETH"));

    assertThat(result).containsKey("BTC");
    assertThat(result).containsKey("ETH");
    assertThat(result.get("BTC").price()).isEqualByComparingTo("42000.50");
    assertThat(result.get("ETH").price()).isEqualByComparingTo("2500.00");
  }

  @Test
  void getQuotes_returns_empty_map_when_no_crypto_symbols() {
    Map<String, Quote> result = adapter.getQuotes(List.of("AAPL", "CW8.PA"));
    assertThat(result).isEmpty();
  }

  @Test
  void getQuotes_returns_empty_map_when_restclient_throws() {
    when(restClient.get()).thenThrow(new RuntimeException("timeout"));

    Map<String, Quote> result = adapter.getQuotes(List.of("BTC"));

    assertThat(result).isEmpty();
  }
}
