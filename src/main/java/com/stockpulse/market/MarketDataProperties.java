package com.stockpulse.market;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.market-data")
public record MarketDataProperties(
        long pollIntervalMs,
        long quoteTtlSeconds,
        boolean fallbackToMockOnProviderError
) {
}
