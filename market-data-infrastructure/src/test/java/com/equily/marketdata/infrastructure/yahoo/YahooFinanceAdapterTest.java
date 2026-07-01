package com.equily.marketdata.infrastructure.yahoo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.equily.portfolio.domain.marketdata.Quote;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
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
class YahooFinanceAdapterTest {

  @Mock private RestClient.Builder builder;
  @Mock private RestClient restClient;
  @Mock private RestClient.RequestHeadersUriSpec requestSpec;
  @Mock private RestClient.ResponseSpec responseSpec;

  private YahooFinanceAdapter adapter;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    when(builder.build()).thenReturn(restClient);
    adapter = new YahooFinanceAdapter(builder, objectMapper);
  }

  private void mockResponse(String body) {
    when(restClient.get()).thenReturn(requestSpec);
    when(requestSpec.uri(anyString())).thenReturn(requestSpec);
    when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
    when(requestSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(String.class)).thenReturn(body);
  }

  private String validYahooJson(String symbol, String price, String prevClose) {
    return """
           {"chart":{"result":[{"meta":{
             "symbol":"%s",
             "regularMarketPrice":%s,
             "chartPreviousClose":%s,
             "currency":"EUR",
             "longName":"Test Corp"
           }}],"error":null}}
           """
        .formatted(symbol, price, prevClose);
  }

  @Test
  void getQuote_returns_quote_for_valid_yahoo_response() {
    mockResponse(validYahooJson("CW8.PA", "400.50", "395.00"));

    Optional<Quote> result = adapter.getQuote("CW8.PA");

    assertThat(result).isPresent();
    assertThat(result.get().symbol()).isEqualTo("CW8.PA");
    assertThat(result.get().price()).isEqualByComparingTo("400.50");
    assertThat(result.get().currency()).isEqualTo("EUR");
    assertThat(result.get().name()).isEqualTo("Test Corp");
  }

  @Test
  void getQuote_computes_changePercent_correctly() {
    // prevClose=400, price=420 => change=(420-400)/400*100 = 5.00%
    mockResponse(validYahooJson("AAPL", "420", "400"));

    Optional<Quote> result = adapter.getQuote("AAPL");

    assertThat(result).isPresent();
    assertThat(result.get().changePercent()).isEqualByComparingTo("5.00");
  }

  @Test
  void getQuote_returns_empty_when_result_array_is_empty() {
    String json =
        """
        {"chart":{"result":[],"error":null}}
        """;
    mockResponse(json);

    Optional<Quote> result = adapter.getQuote("AAPL");

    assertThat(result).isEmpty();
  }

  @Test
  void getQuote_returns_empty_when_chart_error_is_not_null() {
    String json =
        """
        {"chart":{"result":null,"error":{"code":"Not Found","description":"No fundamentals data"}}}
        """;
    mockResponse(json);

    Optional<Quote> result = adapter.getQuote("UNKNOWN");

    assertThat(result).isEmpty();
  }

  @Test
  void getQuote_returns_empty_when_restclient_throws() {
    when(restClient.get()).thenThrow(new RuntimeException("timeout"));

    Optional<Quote> result = adapter.getQuote("AAPL");

    assertThat(result).isEmpty();
  }

  @Test
  void getQuotes_returns_map_for_multiple_valid_symbols() {
    when(restClient.get()).thenReturn(requestSpec);
    when(requestSpec.uri(anyString())).thenReturn(requestSpec);
    when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
    when(requestSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(String.class))
        .thenReturn(validYahooJson("CW8.PA", "400", "390"))
        .thenReturn(validYahooJson("AAPL", "150", "148"));

    Map<String, Quote> result = adapter.getQuotes(List.of("CW8.PA", "AAPL"));

    assertThat(result).containsKey("CW8.PA");
    assertThat(result).containsKey("AAPL");
  }

  @Test
  void getHistoricalClose_returns_rate_when_yahoo_responds_with_valid_data() {
    String json =
        """
        {"chart":{"result":[{"indicators":{"quote":[{"close":[0.921500]}]}}]}}
        """;
    mockResponse(json);

    Optional<BigDecimal> result =
        adapter.getHistoricalClose("USDEUR=X", LocalDate.of(2026, Month.JANUARY, 15));

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualByComparingTo("0.921500");
  }

  @Test
  void getHistoricalClose_returns_empty_when_result_array_is_empty() {
    String json =
        """
        {"chart":{"result":[],"error":null}}
        """;
    mockResponse(json);

    Optional<BigDecimal> result =
        adapter.getHistoricalClose("USDEUR=X", LocalDate.of(2026, Month.JANUARY, 15));

    assertThat(result).isEmpty();
  }

  @Test
  void getHistoricalClose_returns_empty_when_restclient_throws() {
    when(restClient.get()).thenThrow(new RuntimeException("timeout"));

    Optional<BigDecimal> result =
        adapter.getHistoricalClose("USDEUR=X", LocalDate.of(2026, Month.JANUARY, 15));

    assertThat(result).isEmpty();
  }

  @Test
  void getHistoricalClose_returns_empty_when_closes_array_is_empty() {
    String json =
        """
        {"chart":{"result":[{"indicators":{"quote":[{"close":[]}]}}]}}
        """;
    mockResponse(json);

    Optional<BigDecimal> result =
        adapter.getHistoricalClose("USDEUR=X", LocalDate.of(2026, Month.JANUARY, 15));

    assertThat(result).isEmpty();
  }

  @Test
  void getHistoricalPrices_returns_prices_for_valid_response() {
    String json =
        """
        {"chart":{"result":[{
          "timestamp":[1704067200,1704153600],
          "indicators":{"quote":[{"close":[150.0000,155.5000]}]}
        }]}}
        """;
    mockResponse(json);

    Map<LocalDate, BigDecimal> result =
        adapter.getHistoricalPrices(
            "AAPL", LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.JANUARY, 2));

    assertThat(result).hasSize(2);
    assertThat(result.values())
        .extracting(BigDecimal::doubleValue)
        .containsExactlyInAnyOrder(150.0, 155.5);
  }

  @Test
  void getHistoricalPrices_returns_empty_on_http_failure() {
    when(restClient.get()).thenThrow(new RuntimeException("timeout"));

    Map<LocalDate, BigDecimal> result =
        adapter.getHistoricalPrices(
            "AAPL", LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.JANUARY, 2));

    assertThat(result).isEmpty();
  }

  @Test
  void getHistoricalPrices_returns_empty_on_empty_result_array() {
    mockResponse(
        """
        {"chart":{"result":[]}}
        """);

    Map<LocalDate, BigDecimal> result =
        adapter.getHistoricalPrices(
            "AAPL", LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.JANUARY, 2));

    assertThat(result).isEmpty();
  }

  @Test
  void parseHistoricalPrices_skips_null_close_values() {
    String json =
        """
        {"chart":{"result":[{
          "timestamp":[1704067200,1704153600,1704240000],
          "indicators":{"quote":[{"close":[150.0000,null,155.5000]}]}
        }]}}
        """;

    Map<LocalDate, BigDecimal> result = adapter.parseHistoricalPrices(json);

    assertThat(result).hasSize(2);
    assertThat(result.values())
        .extracting(BigDecimal::doubleValue)
        .containsExactlyInAnyOrder(150.0, 155.5);
  }

  @Test
  void parseHistoricalClose_skips_null_entries_and_returns_last_non_null() {
    String json =
        """
        {"chart":{"result":[{"indicators":{"quote":[{"close":[null,0.915000,0.921500]}]}}]}}
        """;

    Optional<BigDecimal> result = adapter.parseHistoricalClose(json);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualByComparingTo("0.921500");
  }
}
