package com.stockpulse.portfolio;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.portfolio")
public record PortfolioProperties(BigDecimal initialCashBalance) {
}
