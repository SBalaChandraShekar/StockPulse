package com.stockpulse.market;

import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CachedQuoteService {

    private final MarketDataService marketDataService;

    public CachedQuoteService(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @Cacheable(cacheNames = "quotes", key = "#symbol.toUpperCase()", sync = true)
    public MarketQuote fetchTradableQuote(String symbol) {
        return marketDataService.retrieveTradableQuote(symbol);
    }

    @CachePut(cacheNames = "quotes", key = "#symbol.toUpperCase()")
    public MarketQuote refreshQuote(String symbol) {
        return marketDataService.retrieveTradableQuote(symbol);
    }
}
