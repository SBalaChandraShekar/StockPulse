package com.stockpulse.alerts;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AlertMailProperties.class)
public class AlertConfig {
}
