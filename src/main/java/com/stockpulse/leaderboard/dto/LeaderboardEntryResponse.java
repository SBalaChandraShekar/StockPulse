package com.stockpulse.leaderboard.dto;

import java.math.BigDecimal;

public record LeaderboardEntryResponse(
        int rank,
        String email,
        BigDecimal totalPortfolioValue,
        BigDecimal percentGain
) {
}
