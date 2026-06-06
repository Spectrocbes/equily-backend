package com.equily.marketdata.infrastructure.fmp;

import com.equily.portfolio.domain.marketdata.Quote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FmpAdapter {

  private static final Logger log = LoggerFactory.getLogger(FmpAdapter.class);

  private static final String BASE_URL = "https://financialmodelingprep.com/stable/quote";

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String apiKey;

  FmpAdapter(
      RestClient.Builder builder,
      ObjectMapper objectMapper,
      @Value("${market-data.fmp.api-key:demo}") String apiKey) {
    this.restClient = builder.build();
    this.objectMapper = objectMapper;
    this.apiKey = apiKey;
  }

  public Optional<Quote> getQuote(String symbol) {
    if (symbol.contains(".")) return Optional.empty();
    try {
      String response =
          restClient
              .get()
              .uri(BASE_URL + "?symbol=" + symbol + "&apikey=" + apiKey)
              .retrieve()
              .body(String.class);
      return parseResponse(symbol, response);
    } catch (Exception e) {
      log.warn("FMP failed for {}: {}", symbol, e.getMessage());
      return Optional.empty();
    }
  }

  public Map<String, Quote> getQuotes(List<String> symbols) {
    return symbols.stream()
        .filter(s -> !s.contains("."))
        .map(s -> Map.entry(s, getQuote(s)))
        .filter(e -> e.getValue().isPresent())
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
  }

  private Optional<Quote> parseResponse(String symbol, String json) {
    try {
      JsonNode root = objectMapper.readTree(json);
      if (!root.isArray() || root.isEmpty()) return Optional.empty();
      JsonNode item = root.get(0);
      if (item.path("price").isMissingNode()) return Optional.empty();

      BigDecimal price = new BigDecimal(item.path("price").asText());
      String name = item.path("name").asText(symbol);
      BigDecimal change =
          new BigDecimal(item.path("changePercentage").asText("0"))
              .setScale(2, RoundingMode.HALF_EVEN);

      return Optional.of(new Quote(symbol, price, "USD", name, Instant.now(), change));
    } catch (Exception e) {
      log.warn("FMP parse failed for {}: {}", symbol, e.getMessage());
      return Optional.empty();
    }
  }
}
