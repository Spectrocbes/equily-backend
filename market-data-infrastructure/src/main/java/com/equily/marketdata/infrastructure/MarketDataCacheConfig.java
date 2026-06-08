package com.equily.marketdata.infrastructure;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableCaching
@EnableScheduling
public class MarketDataCacheConfig {

  @Bean
  public CacheManager cacheManager() {
    CaffeineCache quotesCache =
        new CaffeineCache(
            "quotes",
            Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(1000)
                .recordStats()
                .build());
    CaffeineCache fxRatesCache =
        new CaffeineCache(
            "fxRates",
            Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(20)
                .recordStats()
                .build());
    SimpleCacheManager manager = new SimpleCacheManager();
    manager.setCaches(List.of(quotesCache, fxRatesCache));
    return manager;
  }
}
