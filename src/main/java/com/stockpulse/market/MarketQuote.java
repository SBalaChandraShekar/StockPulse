package com.stockpulse.market;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record MarketQuote(
        String symbol,
        BigDecimal price,
        String source,
        Instant fetchedAt
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
