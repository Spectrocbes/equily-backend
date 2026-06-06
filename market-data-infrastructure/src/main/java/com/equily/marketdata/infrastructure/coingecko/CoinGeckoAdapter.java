package com.equily.marketdata.infrastructure.coingecko;

import com.equily.marketdata.infrastructure.CryptoSymbols;
import com.equily.portfolio.domain.marketdata.Quote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CoinGeckoAdapter {

  private static final Logger log = LoggerFactory.getLogger(CoinGeckoAdapter.class);

  private static final String BASE_URL = "https://api.coingecko.com/api/v3/simple/price";

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  CoinGeckoAdapter(RestClient.Builder builder, ObjectMapper objectMapper) {
    this.restClient = builder.build();
    this.objectMapper = objectMapper;
  }

  public Optional<Quote> getQuote(String symbol) {
    return CryptoSymbols.coingeckoId(symbol)
        .flatMap(
            id -> {
              try {
                String url =
                    BASE_URL + "?ids=" + id + "&vs_currencies=eur&include_24hr_change=true";
                String response =
                    restClient
                        .get()
                        .uri(url)
                        .header("User-Agent", "Equily/1.0")
                        .retrieve()
                        .body(String.class);
                return parseCoinGeckoResponse(symbol, id, response);
              } catch (Exception e) {
                log.warn("CoinGecko failed for {}: {}", symbol, e.getMessage());
                return Optional.empty();
              }
            });
  }

  public Map<String, Quote> getQuotes(List<String> symbols) {
    List<String> cryptoSymbols = symbols.stream().filter(CryptoSymbols::isCrypto).toList();
    if (cryptoSymbols.isEmpty()) return Map.of();

    String ids =
        cryptoSymbols.stream()
            .map(s -> CryptoSymbols.coingeckoId(s).orElse(""))
            .filter(id -> !id.isBlank())
            .distinct()
            .collect(Collectors.joining(","));

    try {
      String url = BASE_URL + "?ids=" + ids + "&vs_currencies=eur&include_24hr_change=true";
      String response =
          restClient
              .get()
              .uri(url)
              .header("User-Agent", "Equily/1.0")
              .retrieve()
              .body(String.class);
      return parseBatchCoinGeckoResponse(cryptoSymbols, response);
    } catch (Exception e) {
      log.warn("CoinGecko batch failed: {}", e.getMessage());
      return Map.of();
    }
  }

  private Optional<Quote> parseCoinGeckoResponse(String symbol, String id, String json) {
    try {
      JsonNode root = objectMapper.readTree(json);
      JsonNode data = root.path(id);
      if (data.isMissingNode()) return Optional.empty();

      BigDecimal price = new BigDecimal(data.path("eur").asText());
      BigDecimal change =
          new BigDecimal(data.path("eur_24h_change").asText("0"))
              .setScale(2, RoundingMode.HALF_EVEN);

      return Optional.of(
          new Quote(
              symbol.toUpperCase(), price, "EUR", symbol.toUpperCase(), Instant.now(), change));
    } catch (Exception e) {
      log.warn("Parse error CoinGecko for {}: {}", symbol, e.getMessage());
      return Optional.empty();
    }
  }

  private Map<String, Quote> parseBatchCoinGeckoResponse(List<String> symbols, String json) {
    Map<String, Quote> result = new HashMap<>();
    try {
      JsonNode root = objectMapper.readTree(json);
      for (String symbol : symbols) {
        CryptoSymbols.coingeckoId(symbol)
            .ifPresent(
                id -> {
                  JsonNode data = root.path(id);
                  if (!data.isMissingNode()) {
                    try {
                      BigDecimal price = new BigDecimal(data.path("eur").asText());
                      BigDecimal change =
                          new BigDecimal(data.path("eur_24h_change").asText("0"))
                              .setScale(2, RoundingMode.HALF_EVEN);
                      result.put(
                          symbol.toUpperCase(),
                          new Quote(
                              symbol.toUpperCase(),
                              price,
                              "EUR",
                              symbol.toUpperCase(),
                              Instant.now(),
                              change));
                    } catch (Exception ignored) {
                    }
                  }
                });
      }
    } catch (Exception e) {
      log.warn("Parse error CoinGecko batch: {}", e.getMessage());
    }
    return result;
  }
}
