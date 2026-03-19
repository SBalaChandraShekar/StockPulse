package com.stockpulse.market;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketDataController {

    private final CachedQuoteService cachedQuoteService;

    public MarketDataController(CachedQuoteService cachedQuoteService) {
        this.cachedQuoteService = cachedQuoteService;
    }

    @GetMapping("/{symbol}/quote")
    public Map<String, Object> quote(@PathVariable String symbol) {
        MarketQuote quote = cachedQuoteService.fetchTradableQuote(symbol);
        return Map.of(
                "symbol", quote.symbol(),
                "price", quote.price(),
                "source", quote.source(),
                "fetchedAt", quote.fetchedAt());
    }
}
