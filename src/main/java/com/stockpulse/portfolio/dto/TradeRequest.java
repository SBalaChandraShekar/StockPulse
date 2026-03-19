package com.stockpulse.portfolio.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TradeRequest(
        @NotBlank String symbol,
        @Min(1) int quantity
) {
}
