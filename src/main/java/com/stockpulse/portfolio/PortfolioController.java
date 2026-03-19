package com.stockpulse.portfolio;

import com.stockpulse.portfolio.dto.PortfolioSummaryResponse;
import com.stockpulse.portfolio.dto.TradeRequest;
import com.stockpulse.portfolio.dto.TradeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
@Tag(name = "Portfolio", description = "Portfolio summary and trade management")
@SecurityRequirement(name = "bearer-jwt")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    @Operation(summary = "View current portfolio", description = "Returns cash balance, holdings, and portfolio valuation")
    public PortfolioSummaryResponse viewPortfolio(Authentication authentication) {
        return portfolioService.viewPortfolio(authentication.getName());
    }

    @GetMapping("/trades")
    @Operation(summary = "View trade history", description = "Returns the authenticated user's trades ordered by most recent first")
    public List<TradeResponse> viewTradeHistory(Authentication authentication) {
        return portfolioService.viewTradeHistory(authentication.getName());
    }

    @PostMapping("/buy")
    @Operation(summary = "Buy a stock", description = "Validates ticker and cash balance, then updates holdings atomically")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trade executed"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content(schema = @Schema())),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema()))
    })
    public TradeResponse buyStock(Authentication authentication, @Valid @RequestBody TradeRequest request) {
        return portfolioService.buyStock(authentication.getName(), request);
    }

    @PostMapping("/sell")
    @Operation(summary = "Sell a stock", description = "Validates ticker and quantity, then updates holdings atomically")
    public TradeResponse sellStock(Authentication authentication, @Valid @RequestBody TradeRequest request) {
        return portfolioService.sellStock(authentication.getName(), request);
    }
}
