package com.stockpulse.alerts.dto;

import com.stockpulse.domain.PriceAlertDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreatePriceAlertRequest(
        @NotBlank String symbol,
        @NotNull PriceAlertDirection direction,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal targetPrice
) {
}
