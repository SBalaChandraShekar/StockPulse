package com.stockpulse.market;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.alpha-vantage")
public record AlphaVantageProperties(
        String baseUrl,
        String apiKey
) {
}
