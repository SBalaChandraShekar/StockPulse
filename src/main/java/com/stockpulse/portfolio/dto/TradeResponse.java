package com.stockpulse.portfolio.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeResponse(
        Long id,
        String symbol,
        String tradeType,
        int quantity,
        BigDecimal executedPrice,
        BigDecimal totalAmount,
        Instant executedAt
) {
}
