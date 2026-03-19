package com.stockpulse.portfolio.dto;

import java.math.BigDecimal;

public record HoldingResponse(
        String symbol,
        int quantity,
        BigDecimal averageBuyPrice,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedProfitLoss
) {
}
