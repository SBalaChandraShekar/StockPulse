package com.stockpulse.market;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties({AlphaVantageProperties.class, MarketDataProperties.class})
public class MarketDataConfig {

    @Bean
    WebClient alphaVantageWebClient(WebClient.Builder builder, AlphaVantageProperties alphaVantageProperties) {
        return builder.baseUrl(alphaVantageProperties.baseUrl()).build();
    }
}
