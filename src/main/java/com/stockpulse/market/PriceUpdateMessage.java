package com.stockpulse.market;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceUpdateMessage(
        String symbol,
        BigDecimal price,
        String source,
        Instant fetchedAt
) {

    public static PriceUpdateMessage fromQuote(MarketQuote quote) {
        return new PriceUpdateMessage(
                quote.symbol(),
                quote.price(),
                quote.source(),
                quote.fetchedAt());
    }
}
