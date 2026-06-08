package com.equily.marketdata.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = {MarketDataCacheConfig.class})
@Import(MarketDataCacheConfig.class)
class MarketDataCacheConfigTest {

  @Autowired private CacheManager cacheManager;

  @Test
  void cacheManager_bean_is_created() {
    assertThat(cacheManager).isNotNull();
  }

  @Test
  void quotes_cache_is_registered() {
    assertThat(cacheManager.getCache("quotes")).isNotNull();
  }

  @Test
  void quotes_cache_is_initially_empty() {
    Cache quotesCache = cacheManager.getCache("quotes");
    assertThat(quotesCache).isNotNull();
    // Caffeine cache starts empty — get on unknown key returns null
    assertThat(quotesCache.get("unknown-key")).isNull();
  }
}
