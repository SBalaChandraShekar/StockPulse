package com.stockpulse.alerts.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceAlertResponse(
        Long id,
        String symbol,
        String direction,
        BigDecimal targetPrice,
        boolean triggered,
        Instant createdAt,
        Instant triggeredAt
) {
}
