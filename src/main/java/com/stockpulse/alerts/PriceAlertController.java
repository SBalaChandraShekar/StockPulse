package com.stockpulse.alerts;

import com.stockpulse.alerts.dto.CreatePriceAlertRequest;
import com.stockpulse.alerts.dto.PriceAlertResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Alerts", description = "Price alert management")
@SecurityRequirement(name = "bearer-jwt")
public class PriceAlertController {

    private final PriceAlertService priceAlertService;

    public PriceAlertController(PriceAlertService priceAlertService) {
        this.priceAlertService = priceAlertService;
    }

    @GetMapping
    @Operation(summary = "List my price alerts")
    public List<PriceAlertResponse> listAlerts(Authentication authentication) {
        return priceAlertService.listAlerts(authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a price alert")
    public PriceAlertResponse createAlert(Authentication authentication,
                                          @Valid @RequestBody CreatePriceAlertRequest request) {
        return priceAlertService.createAlert(authentication.getName(), request);
    }
}
