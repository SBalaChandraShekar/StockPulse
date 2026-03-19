package com.stockpulse.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class PriceBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(PriceBroadcastService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public PriceBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishPriceUpdate(MarketQuote quote) {
        String symbol = quote.symbol().toUpperCase();
        messagingTemplate.convertAndSend("/topic/prices/" + symbol, PriceUpdateMessage.fromQuote(quote));
        log.debug("Published price update to /topic/prices/{}", symbol);
    }
}
