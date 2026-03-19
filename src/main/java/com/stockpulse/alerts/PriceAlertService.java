package com.stockpulse.alerts;

import com.stockpulse.alerts.dto.CreatePriceAlertRequest;
import com.stockpulse.alerts.dto.PriceAlertResponse;
import com.stockpulse.domain.AppUser;
import com.stockpulse.domain.PriceAlert;
import com.stockpulse.market.MarketQuote;
import com.stockpulse.repository.AppUserRepository;
import com.stockpulse.repository.PriceAlertRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PriceAlertService {

    private final PriceAlertRepository priceAlertRepository;
    private final AppUserRepository appUserRepository;
    private final AlertEmailService alertEmailService;

    public PriceAlertService(PriceAlertRepository priceAlertRepository,
                             AppUserRepository appUserRepository,
                             AlertEmailService alertEmailService) {
        this.priceAlertRepository = priceAlertRepository;
        this.appUserRepository = appUserRepository;
        this.alertEmailService = alertEmailService;
    }

    @Transactional
    public PriceAlertResponse createAlert(String userEmail, CreatePriceAlertRequest request) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        PriceAlert alert = new PriceAlert(
                user,
                request.symbol().trim().toUpperCase(Locale.ROOT),
                request.direction(),
                request.targetPrice().setScale(4, RoundingMode.HALF_UP));

        return toResponse(priceAlertRepository.save(alert));
    }

    @Transactional(readOnly = true)
    public List<PriceAlertResponse> listAlerts(String userEmail) {
        return priceAlertRepository.findByUserEmailIgnoreCaseOrderByCreatedAtDesc(userEmail).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public int evaluateAlerts(String symbol, MarketQuote quote) {
        int triggeredCount = 0;
        for (PriceAlert alert : priceAlertRepository.findBySymbolIgnoreCaseAndTriggeredFalse(symbol)) {
            if (!shouldTrigger(alert, quote.price())) {
                continue;
            }

            alert.markTriggered();
            alertEmailService.sendAlert(alert, quote);
            triggeredCount++;
        }
        return triggeredCount;
    }

    boolean shouldTrigger(PriceAlert alert, BigDecimal currentPrice) {
        return switch (alert.getDirection()) {
            case ABOVE -> currentPrice.compareTo(alert.getTargetPrice()) >= 0;
            case BELOW -> currentPrice.compareTo(alert.getTargetPrice()) <= 0;
        };
    }

    private PriceAlertResponse toResponse(PriceAlert alert) {
        return new PriceAlertResponse(
                alert.getId(),
                alert.getSymbol(),
                alert.getDirection().name(),
                alert.getTargetPrice(),
                alert.isTriggered(),
                alert.getCreatedAt(),
                alert.getTriggeredAt());
    }
}
