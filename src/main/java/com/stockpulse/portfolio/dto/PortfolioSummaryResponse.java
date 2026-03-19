package com.stockpulse.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioSummaryResponse(
        String ownerEmail,
        BigDecimal cashBalance,
        BigDecimal holdingsMarketValue,
        BigDecimal totalPortfolioValue,
        List<HoldingResponse> holdings
) {
}
