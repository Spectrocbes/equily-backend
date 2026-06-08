package com.equily.marketdata.infrastructure.alphavantage;

import com.equily.portfolio.domain.marketdata.Quote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AlphaVantageAdapter {

  private static final Logger log = LoggerFactory.getLogger(AlphaVantageAdapter.class);

  private static final String BASE_URL = "https://www.alphavantage.co/query";
  private static final int DAILY_LIMIT = 20;

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String apiKey;
  private final AtomicInteger dailyCallCount = new AtomicInteger(0);

  AlphaVantageAdapter(
      RestClient.Builder builder,
      ObjectMapper objectMapper,
      @Value("${market-data.alpha-vantage.api-key:demo}") String apiKey) {
    this.restClient = builder.build();
    this.objectMapper = objectMapper;
    this.apiKey = apiKey;
  }

  @Scheduled(cron = "0 0 0 * * *")
  public void resetDailyCount() {
    dailyCallCount.set(0);
    log.info("Alpha Vantage daily call count reset");
  }

  public Optional<Quote> getQuote(String symbol) {
    if (dailyCallCount.get() >= DAILY_LIMIT) {
      log.warn("Alpha Vantage daily limit reached — skipping {}", symbol);
      return Optional.empty();
    }
    try {
      String response =
          restClient
              .get()
              .uri(BASE_URL + "?function=GLOBAL_QUOTE&symbol=" + symbol + "&apikey=" + apiKey)
              .retrieve()
              .body(String.class);
      dailyCallCount.incrementAndGet();
      return parseResponse(symbol, response);
    } catch (Exception e) {
      log.warn("Alpha Vantage failed for {}: {}", symbol, e.getMessage());
      return Optional.empty();
    }
  }

  public Map<String, Quote> getQuotes(List<String> symbols) {
    return symbols.stream()
        .map(s -> Map.entry(s, getQuote(s)))
        .filter(e -> e.getValue().isPresent())
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
  }

  private Optional<Quote> parseResponse(String symbol, String json) {
    try {
      JsonNode root = objectMapper.readTree(json);
      JsonNode quote = root.path("Global Quote");
      if (quote.isMissingNode() || quote.isEmpty()) return Optional.empty();

      String priceStr = quote.path("05. price").asText("");
      if (priceStr.isBlank()) return Optional.empty();

      BigDecimal price = new BigDecimal(priceStr);
      String changeStr = quote.path("10. change percent").asText("0%").replace("%", "").trim();
      BigDecimal change = new BigDecimal(changeStr).setScale(2, RoundingMode.HALF_EVEN);

      String currency =
          symbol.endsWith(".PA")
                  || symbol.endsWith(".AS")
                  || symbol.endsWith(".DE")
                  || symbol.endsWith(".MI")
                  || symbol.endsWith(".BR")
              ? "EUR"
              : "USD";

      return Optional.of(new Quote(symbol, price, currency, symbol, Instant.now(), change));
    } catch (Exception e) {
      log.warn("Alpha Vantage parse failed for {}: {}", symbol, e.getMessage());
      return Optional.empty();
    }
  }
}
