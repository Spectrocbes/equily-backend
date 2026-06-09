package com.equily.marketdata.infrastructure.yahoo;

import com.equily.portfolio.domain.marketdata.Quote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class YahooFinanceAdapter {

  private static final Logger log = LoggerFactory.getLogger(YahooFinanceAdapter.class);

  private static final String BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  YahooFinanceAdapter(RestClient.Builder builder, ObjectMapper objectMapper) {
    this.restClient = builder.build();
    this.objectMapper = objectMapper;
  }

  public Optional<Quote> getQuote(String symbol) {
    try {
      String response =
          restClient
              .get()
              .uri(BASE_URL + symbol + "?interval=1d&range=1d")
              .header("User-Agent", "Mozilla/5.0 (compatible; Equily/1.0)")
              .retrieve()
              .body(String.class);
      return parseResponse(symbol, response);
    } catch (Exception e) {
      log.warn("Yahoo Finance failed for {}: {}", symbol, e.getMessage());
      return Optional.empty();
    }
  }

  public Map<String, Quote> getQuotes(List<String> symbols) {
    return symbols.stream()
        .map(s -> Map.entry(s, getQuote(s)))
        .filter(e -> e.getValue().isPresent())
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
  }

  /**
   * Fetches the closing price for a symbol on a given date. Returns empty if the date has no
   * trading data (weekend/holiday) or if the HTTP call fails.
   */
  public Optional<BigDecimal> getHistoricalClose(String symbol, LocalDate date) {
    long period1 = date.toEpochDay() * 86400L;
    long period2 = date.plusDays(1).toEpochDay() * 86400L;
    String url = BASE_URL + symbol + "?interval=1d&period1=" + period1 + "&period2=" + period2;
    try {
      String response =
          restClient
              .get()
              .uri(url)
              .header("User-Agent", "Mozilla/5.0 (compatible; Equily/1.0)")
              .retrieve()
              .body(String.class);
      return parseHistoricalClose(response);
    } catch (Exception e) {
      log.warn("Historical close failed for {} on {}: {}", symbol, date, e.getMessage());
      return Optional.empty();
    }
  }

  Optional<BigDecimal> parseHistoricalClose(String json) {
    try {
      JsonNode root = objectMapper.readTree(json);
      JsonNode result = root.path("chart").path("result");
      if (result.isEmpty() || result.isNull()) return Optional.empty();

      JsonNode closes = result.get(0).path("indicators").path("quote").get(0).path("close");
      if (closes == null || closes.isEmpty()) return Optional.empty();

      BigDecimal rate = null;
      for (JsonNode close : closes) {
        if (!close.isNull()) {
          rate = new BigDecimal(close.asText()).setScale(6, RoundingMode.HALF_EVEN);
        }
      }
      return Optional.ofNullable(rate);
    } catch (Exception e) {
      log.warn("Failed to parse historical close: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private Optional<Quote> parseResponse(String symbol, String json) {
    try {
      JsonNode root = objectMapper.readTree(json);
      JsonNode chart = root.path("chart");
      JsonNode result = chart.path("result");

      if (result.isEmpty() || result.isNull() || !chart.path("error").isNull()) {
        return Optional.empty();
      }

      JsonNode meta = result.get(0).path("meta");
      BigDecimal price = new BigDecimal(meta.path("regularMarketPrice").asText());
      String currency = meta.path("currency").asText("USD");
      String name = meta.path("longName").asText(meta.path("shortName").asText(symbol));

      BigDecimal prevClose = new BigDecimal(meta.path("chartPreviousClose").asText("0"));
      BigDecimal changePercent =
          prevClose.compareTo(BigDecimal.ZERO) == 0
              ? BigDecimal.ZERO
              : price
                  .subtract(prevClose)
                  .divide(prevClose, 4, RoundingMode.HALF_EVEN)
                  .multiply(new BigDecimal("100"))
                  .setScale(2, RoundingMode.HALF_EVEN);

      return Optional.of(new Quote(symbol, price, currency, name, Instant.now(), changePercent));
    } catch (Exception e) {
      log.warn("Yahoo parse failed for {}: {}", symbol, e.getMessage());
      return Optional.empty();
    }
  }
}
