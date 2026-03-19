package com.stockpulse.alerts;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.alerts.mail")
public record AlertMailProperties(
        boolean enabled,
        String from
) {
}
