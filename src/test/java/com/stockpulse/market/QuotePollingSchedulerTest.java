package com.stockpulse.market;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockpulse.alerts.PriceAlertService;
import com.stockpulse.repository.HoldingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuotePollingSchedulerTest {

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private CachedQuoteService cachedQuoteService;

    @Mock
    private PriceBroadcastService priceBroadcastService;

    @Mock
    private PriceAlertService priceAlertService;

    @InjectMocks
    private QuotePollingScheduler quotePollingScheduler;

    @Test
    void refreshTrackedQuotesRefreshesBroadcastsAndEvaluatesAlerts() {
        MarketQuote quote = new MarketQuote("AAPL", new BigDecimal("123.4500"), "mock", Instant.now());
        when(holdingRepository.findDistinctTrackedSymbols()).thenReturn(List.of("AAPL"));
        when(cachedQuoteService.refreshQuote("AAPL")).thenReturn(quote);

        quotePollingScheduler.refreshTrackedQuotes();

        verify(priceBroadcastService).publishPriceUpdate(quote);
        verify(priceAlertService).evaluateAlerts("AAPL", quote);
    }
}
