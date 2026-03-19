package com.stockpulse.leaderboard;

import com.stockpulse.leaderboard.dto.LeaderboardEntryResponse;
import com.stockpulse.portfolio.PortfolioProperties;
import com.stockpulse.portfolio.PortfolioService;
import com.stockpulse.repository.PortfolioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaderboardService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioService portfolioService;
    private final PortfolioProperties portfolioProperties;

    public LeaderboardService(PortfolioRepository portfolioRepository,
                              PortfolioService portfolioService,
                              PortfolioProperties portfolioProperties) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioService = portfolioService;
        this.portfolioProperties = portfolioProperties;
    }

    @Transactional
    public List<LeaderboardEntryResponse> getLeaderboard() {
        BigDecimal baseline = portfolioProperties.initialCashBalance().setScale(2, RoundingMode.HALF_UP);

        return portfolioRepository.findAll().stream()
                .map(portfolio -> {
                    BigDecimal totalValue = portfolioService.viewPortfolio(portfolio.getOwner().getEmail()).totalPortfolioValue();
                    BigDecimal percentGain = totalValue.subtract(baseline)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(baseline, 2, RoundingMode.HALF_UP);
                    return new LeaderboardEntryResponse(0, portfolio.getOwner().getEmail(), totalValue, percentGain);
                })
                .sorted(Comparator.comparing(LeaderboardEntryResponse::percentGain).reversed())
                .toList();
    }
}
