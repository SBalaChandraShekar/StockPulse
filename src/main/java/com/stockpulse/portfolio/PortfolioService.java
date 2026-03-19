package com.stockpulse.portfolio;

import com.stockpulse.domain.AppUser;
import com.stockpulse.domain.Holding;
import com.stockpulse.domain.Portfolio;
import com.stockpulse.domain.Trade;
import com.stockpulse.domain.TradeType;
import com.stockpulse.market.CachedQuoteService;
import com.stockpulse.market.MarketQuote;
import com.stockpulse.portfolio.dto.HoldingResponse;
import com.stockpulse.portfolio.dto.PortfolioSummaryResponse;
import com.stockpulse.portfolio.dto.TradeRequest;
import com.stockpulse.portfolio.dto.TradeResponse;
import com.stockpulse.repository.AppUserRepository;
import com.stockpulse.repository.HoldingRepository;
import com.stockpulse.repository.PortfolioRepository;
import com.stockpulse.repository.TradeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PortfolioService {

    private static final int PRICE_SCALE = 4;
    private static final int MONEY_SCALE = 2;

    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;
    private final AppUserRepository appUserRepository;
    private final CachedQuoteService cachedQuoteService;
    private final PortfolioProperties portfolioProperties;

    public PortfolioService(PortfolioRepository portfolioRepository,
                            HoldingRepository holdingRepository,
                            TradeRepository tradeRepository,
                            AppUserRepository appUserRepository,
                            CachedQuoteService cachedQuoteService,
                            PortfolioProperties portfolioProperties) {
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.tradeRepository = tradeRepository;
        this.appUserRepository = appUserRepository;
        this.cachedQuoteService = cachedQuoteService;
        this.portfolioProperties = portfolioProperties;
    }

    @Transactional
    public Portfolio createDefaultPortfolio(AppUser owner) {
        Portfolio portfolio = new Portfolio(owner, money(portfolioProperties.initialCashBalance()));
        return portfolioRepository.save(portfolio);
    }

    @Transactional
    public TradeResponse buyStock(String ownerEmail, TradeRequest request) {
        Portfolio portfolio = loadOrCreatePortfolioForUpdate(ownerEmail);
        String symbol = normalizeSymbol(request.symbol());
        MarketQuote quote = cachedQuoteService.fetchTradableQuote(symbol);
        BigDecimal unitPrice = price(quote.price());
        BigDecimal totalCost = money(unitPrice.multiply(BigDecimal.valueOf(request.quantity())));

        if (portfolio.getCashBalance().compareTo(totalCost) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient cash balance for this trade");
        }

        Holding holding = holdingRepository.findByPortfolioAndSymbolIgnoreCase(portfolio, symbol)
                .orElseGet(() -> {
                    Holding newHolding = new Holding(symbol, 0, unitPrice);
                    portfolio.addHolding(newHolding);
                    return newHolding;
                });

        BigDecimal existingCostBasis = holding.getAverageBuyPrice()
                .multiply(BigDecimal.valueOf(holding.getQuantity()));
        BigDecimal incomingCostBasis = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));
        int updatedQuantity = holding.getQuantity() + request.quantity();

        holding.setQuantity(updatedQuantity);
        holding.setAverageBuyPrice(price(
                existingCostBasis.add(incomingCostBasis)
                        .divide(BigDecimal.valueOf(updatedQuantity), PRICE_SCALE, RoundingMode.HALF_UP)));
        holdingRepository.save(holding);

        portfolio.setCashBalance(money(portfolio.getCashBalance().subtract(totalCost)));

        Trade trade = new Trade(symbol, TradeType.BUY, request.quantity(), unitPrice, totalCost);
        portfolio.addTrade(trade);
        tradeRepository.save(trade);

        return toTradeResponse(trade);
    }

    @Transactional
    public TradeResponse sellStock(String ownerEmail, TradeRequest request) {
        Portfolio portfolio = loadOrCreatePortfolioForUpdate(ownerEmail);
        String symbol = normalizeSymbol(request.symbol());
        Holding holding = holdingRepository.findByPortfolioAndSymbolIgnoreCase(portfolio, symbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "You do not own this ticker"));

        if (holding.getQuantity() < request.quantity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient shares to complete this sell");
        }

        MarketQuote quote = cachedQuoteService.fetchTradableQuote(symbol);
        BigDecimal unitPrice = price(quote.price());
        BigDecimal totalProceeds = money(unitPrice.multiply(BigDecimal.valueOf(request.quantity())));

        int remainingQuantity = holding.getQuantity() - request.quantity();
        if (remainingQuantity == 0) {
            portfolio.removeHolding(holding);
            holdingRepository.delete(holding);
        } else {
            holding.setQuantity(remainingQuantity);
            holdingRepository.save(holding);
        }

        portfolio.setCashBalance(money(portfolio.getCashBalance().add(totalProceeds)));

        Trade trade = new Trade(symbol, TradeType.SELL, request.quantity(), unitPrice, totalProceeds);
        portfolio.addTrade(trade);
        tradeRepository.save(trade);

        return toTradeResponse(trade);
    }

    @Transactional
    public PortfolioSummaryResponse viewPortfolio(String ownerEmail) {
        Portfolio portfolio = loadOrCreatePortfolio(ownerEmail);
        List<HoldingResponse> holdings = holdingRepository.findByPortfolioIdOrderBySymbolAsc(portfolio.getId()).stream()
                .map(holding -> {
                    MarketQuote quote = cachedQuoteService.fetchTradableQuote(holding.getSymbol());
                    BigDecimal currentPrice = price(quote.price());
                    BigDecimal marketValue = money(currentPrice.multiply(BigDecimal.valueOf(holding.getQuantity())));
                    BigDecimal costBasis = money(holding.getAverageBuyPrice()
                            .multiply(BigDecimal.valueOf(holding.getQuantity())));
                    return new HoldingResponse(
                            holding.getSymbol(),
                            holding.getQuantity(),
                            price(holding.getAverageBuyPrice()),
                            currentPrice,
                            marketValue,
                            money(marketValue.subtract(costBasis)));
                })
                .toList();

        BigDecimal holdingsMarketValue = money(holdings.stream()
                .map(HoldingResponse::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        return new PortfolioSummaryResponse(
                portfolio.getOwner().getEmail(),
                money(portfolio.getCashBalance()),
                holdingsMarketValue,
                money(portfolio.getCashBalance().add(holdingsMarketValue)),
                holdings);
    }

    @Transactional
    public List<TradeResponse> viewTradeHistory(String ownerEmail) {
        Portfolio portfolio = loadOrCreatePortfolio(ownerEmail);
        return tradeRepository.findByPortfolioIdOrderByExecutedAtDesc(portfolio.getId()).stream()
                .map(this::toTradeResponse)
                .toList();
    }

    private Portfolio loadOrCreatePortfolio(String ownerEmail) {
        return portfolioRepository.findWithOwnerByOwnerEmailIgnoreCase(ownerEmail)
                .orElseGet(() -> createPortfolioForExistingUser(ownerEmail));
    }

    private Portfolio loadOrCreatePortfolioForUpdate(String ownerEmail) {
        return portfolioRepository.findByOwnerEmailIgnoreCase(ownerEmail)
                .orElseGet(() -> createPortfolioForExistingUser(ownerEmail));
    }

    private Portfolio createPortfolioForExistingUser(String ownerEmail) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(ownerEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return createDefaultPortfolio(user);
    }

    private TradeResponse toTradeResponse(Trade trade) {
        return new TradeResponse(
                trade.getId(),
                trade.getSymbol(),
                trade.getTradeType().name(),
                trade.getQuantity(),
                price(trade.getExecutedPrice()),
                money(trade.getTotalAmount()),
                trade.getExecutedAt());
    }

    private String normalizeSymbol(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal price(BigDecimal value) {
        return value.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
