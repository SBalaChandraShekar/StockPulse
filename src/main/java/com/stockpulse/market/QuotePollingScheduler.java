package com.stockpulse.market;

import com.stockpulse.alerts.PriceAlertService;
import com.stockpulse.repository.HoldingRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class QuotePollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuotePollingScheduler.class);

    private final HoldingRepository holdingRepository;
    private final CachedQuoteService cachedQuoteService;
    private final PriceBroadcastService priceBroadcastService;
    private final PriceAlertService priceAlertService;

    public QuotePollingScheduler(HoldingRepository holdingRepository,
                                 CachedQuoteService cachedQuoteService,
                                 PriceBroadcastService priceBroadcastService,
                                 PriceAlertService priceAlertService) {
        this.holdingRepository = holdingRepository;
        this.cachedQuoteService = cachedQuoteService;
        this.priceBroadcastService = priceBroadcastService;
        this.priceAlertService = priceAlertService;
    }

    @Scheduled(fixedRateString = "${app.market-data.poll-interval-ms}")
    public void refreshTrackedQuotes() {
        List<String> symbols = holdingRepository.findDistinctTrackedSymbols();
        if (symbols.isEmpty()) {
            log.debug("No active holdings found for market polling");
            return;
        }

        int refreshedCount = 0;
        int triggeredAlerts = 0;
        for (String symbol : symbols) {
            try {
                MarketQuote quote = cachedQuoteService.refreshQuote(symbol);
                priceBroadcastService.publishPriceUpdate(quote);
                triggeredAlerts += priceAlertService.evaluateAlerts(symbol, quote);
                refreshedCount++;
            } catch (ResponseStatusException ex) {
                log.warn("Failed to refresh quote for {}: {}", symbol, ex.getReason());
            }
        }

        log.info("Refreshed {} cached market quotes and triggered {} alerts", refreshedCount, triggeredAlerts);
    }
}
