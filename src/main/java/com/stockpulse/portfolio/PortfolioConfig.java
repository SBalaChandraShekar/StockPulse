package com.stockpulse.portfolio;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PortfolioProperties.class)
public class PortfolioConfig {
}
