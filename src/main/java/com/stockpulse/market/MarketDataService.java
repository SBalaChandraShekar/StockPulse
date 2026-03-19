package com.stockpulse.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MarketDataService {

    private final WebClient webClient;
    private final AlphaVantageProperties alphaVantageProperties;
    private final MarketDataProperties marketDataProperties;

    public MarketDataService(WebClient alphaVantageWebClient,
                             AlphaVantageProperties alphaVantageProperties,
                             MarketDataProperties marketDataProperties) {
        this.alphaVantageProperties = alphaVantageProperties;
        this.marketDataProperties = marketDataProperties;
        this.webClient = alphaVantageWebClient;
    }

    public Map<String, Object> fetchQuote(String symbol) {
        MarketQuote quote = retrieveTradableQuote(symbol);
        return Map.of(
                "symbol", quote.symbol(),
                "price", quote.price(),
                "source", quote.source(),
                "fetchedAt", quote.fetchedAt());
    }

    MarketQuote retrieveTradableQuote(String symbol) {
        String normalizedSymbol = symbol.toUpperCase();
        Map<String, Object> response = fetchQuotePayload(normalizedSymbol);

        if ("mock".equals(response.get("source"))) {
            return new MarketQuote(normalizedSymbol, mockPrice(normalizedSymbol), "mock", Instant.now());
        }

        Object globalQuoteObject = response.get("Global Quote");
        if (!(globalQuoteObject instanceof Map<?, ?> globalQuote)) {
            if (response.containsKey("Note")) {
                return fallbackOrThrow(
                        normalizedSymbol,
                        HttpStatus.TOO_MANY_REQUESTS,
                        Objects.toString(response.get("Note"), "Market data rate limit reached"));
            }
            if (response.containsKey("Information")) {
                return fallbackOrThrow(
                        normalizedSymbol,
                        HttpStatus.BAD_GATEWAY,
                        Objects.toString(response.get("Information"), "Market data provider returned an error"));
            }
            if (response.containsKey("Error Message")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ticker symbol");
            }
            return fallbackOrThrow(
                    normalizedSymbol,
                    HttpStatus.BAD_GATEWAY,
                    "Unexpected response from market data provider");
        }

        Object priceValue = globalQuote.get("05. price");
        if (priceValue == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ticker symbol");
        }

        try {
            return new MarketQuote(
                    normalizedSymbol,
                    new BigDecimal(priceValue.toString()).setScale(4, RoundingMode.HALF_UP),
                    "alpha-vantage",
                    Instant.now());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Market data provider returned an invalid price");
        }
    }

    private Map<String, Object> fetchQuotePayload(String symbol) {
        String normalizedSymbol = symbol.toUpperCase();

        if ("demo".equalsIgnoreCase(alphaVantageProperties.apiKey())) {
            return Map.of(
                    "symbol", normalizedSymbol,
                    "source", "mock",
                    "message", "Set ALPHA_VANTAGE_API_KEY to enable live Alpha Vantage quotes");
        }

        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/query")
                            .queryParam("function", "GLOBAL_QUOTE")
                            .queryParam("symbol", normalizedSymbol)
                            .queryParam("apikey", alphaVantageProperties.apiKey())
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return response == null ? Map.of("symbol", normalizedSymbol, "message", "No response received") : response;
        } catch (WebClientRequestException ex) {
            String detail = ex.getMostSpecificCause() != null
                    ? ex.getMostSpecificCause().getMessage()
                    : ex.getMessage();
            return fallbackPayloadOrThrow(
                    normalizedSymbol,
                    HttpStatus.BAD_GATEWAY,
                    "Unable to reach market data provider: " + Objects.toString(detail, "unknown network error"));
        } catch (WebClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            String detail = responseBody == null || responseBody.isBlank()
                    ? ex.getStatusCode().toString()
                    : truncate(responseBody);
            return fallbackPayloadOrThrow(
                    normalizedSymbol,
                    HttpStatus.BAD_GATEWAY,
                    "Market data provider returned an HTTP error: " + detail);
        }
    }

    private MarketQuote fallbackOrThrow(String symbol, HttpStatus status, String reason) {
        if (marketDataProperties.fallbackToMockOnProviderError()) {
            return new MarketQuote(symbol, mockPrice(symbol), "fallback-mock", Instant.now());
        }
        throw new ResponseStatusException(status, reason);
    }

    private Map<String, Object> fallbackPayloadOrThrow(String symbol, HttpStatus status, String reason) {
        if (marketDataProperties.fallbackToMockOnProviderError()) {
            return Map.of(
                    "symbol", symbol,
                    "source", "fallback-mock",
                    "message", reason);
        }
        throw new ResponseStatusException(status, reason);
    }

    private BigDecimal mockPrice(String symbol) {
        int seed = Math.abs(symbol.hashCode() % 5000);
        return BigDecimal.valueOf(50 + (seed / 100.0)).setScale(4, RoundingMode.HALF_UP);
    }

    private String truncate(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 180 ? normalized.substring(0, 180) + "..." : normalized;
    }
}
