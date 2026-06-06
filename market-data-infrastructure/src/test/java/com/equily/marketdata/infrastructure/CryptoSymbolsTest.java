package com.equily.marketdata.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CryptoSymbolsTest {

  @Test
  void isCrypto_returns_true_for_btc() {
    assertThat(CryptoSymbols.isCrypto("BTC")).isTrue();
  }

  @Test
  void isCrypto_returns_true_for_eth() {
    assertThat(CryptoSymbols.isCrypto("ETH")).isTrue();
  }

  @Test
  void isCrypto_returns_true_for_lowercase() {
    assertThat(CryptoSymbols.isCrypto("btc")).isTrue();
  }

  @Test
  void isCrypto_returns_false_for_aapl() {
    assertThat(CryptoSymbols.isCrypto("AAPL")).isFalse();
  }

  @Test
  void isCrypto_returns_false_for_eu_ticker() {
    assertThat(CryptoSymbols.isCrypto("CW8.PA")).isFalse();
  }

  @Test
  void coingeckoId_returns_bitcoin_for_btc() {
    assertThat(CryptoSymbols.coingeckoId("BTC")).contains("bitcoin");
  }

  @Test
  void coingeckoId_returns_ethereum_for_eth() {
    assertThat(CryptoSymbols.coingeckoId("ETH")).contains("ethereum");
  }

  @Test
  void coingeckoId_returns_empty_for_unknown_symbol() {
    assertThat(CryptoSymbols.coingeckoId("UNKNOWN")).isEmpty();
  }

  @Test
  void coingeckoId_is_case_insensitive() {
    assertThat(CryptoSymbols.coingeckoId("btc")).contains("bitcoin");
  }
}
