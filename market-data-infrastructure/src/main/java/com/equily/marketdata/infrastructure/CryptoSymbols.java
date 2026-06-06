package com.equily.marketdata.infrastructure;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CryptoSymbols {

  private CryptoSymbols() {}

  public static final Map<String, String> SYMBOL_TO_COINGECKO_ID =
      Map.ofEntries(
          Map.entry("BTC", "bitcoin"),
          Map.entry("ETH", "ethereum"),
          Map.entry("SOL", "solana"),
          Map.entry("BNB", "binancecoin"),
          Map.entry("ADA", "cardano"),
          Map.entry("XRP", "ripple"),
          Map.entry("DOGE", "dogecoin"),
          Map.entry("DOT", "polkadot"),
          Map.entry("MATIC", "matic-network"),
          Map.entry("LINK", "chainlink"),
          Map.entry("AVAX", "avalanche-2"),
          Map.entry("UNI", "uniswap"),
          Map.entry("LTC", "litecoin"),
          Map.entry("ATOM", "cosmos"),
          Map.entry("XLM", "stellar"));

  public static final Set<String> KNOWN_SYMBOLS = SYMBOL_TO_COINGECKO_ID.keySet();

  public static boolean isCrypto(String symbol) {
    return KNOWN_SYMBOLS.contains(symbol.toUpperCase());
  }

  public static Optional<String> coingeckoId(String symbol) {
    return Optional.ofNullable(SYMBOL_TO_COINGECKO_ID.get(symbol.toUpperCase()));
  }
}
