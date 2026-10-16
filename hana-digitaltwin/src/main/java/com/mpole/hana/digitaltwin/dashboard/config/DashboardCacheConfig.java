package com.mpole.hana.digitaltwin.dashboard.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class DashboardCacheConfig {

    public static final String SITUATION_CACHE = "dashboardSituation";
    public static final String POWER_CACHE = "dashboardPower";
    public static final String MECHANICAL_CACHE = "dashboardMechanical";

    @Bean
    public CacheManager dashboardCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                SITUATION_CACHE, POWER_CACHE, MECHANICAL_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(20, TimeUnit.SECONDS)
                .maximumSize(256));
        return manager;
    }
}
