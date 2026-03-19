package com.stockpulse.leaderboard;

import com.stockpulse.leaderboard.dto.LeaderboardEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
@Tag(name = "Leaderboard", description = "Portfolio performance ranking")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping
    @Operation(summary = "View leaderboard ranked by portfolio % gain")
    public List<LeaderboardEntryResponse> leaderboard() {
        List<LeaderboardEntryResponse> entries = leaderboardService.getLeaderboard();
        return IntStream.range(0, entries.size())
                .mapToObj(index -> new LeaderboardEntryResponse(
                        index + 1,
                        entries.get(index).email(),
                        entries.get(index).totalPortfolioValue(),
                        entries.get(index).percentGain()))
                .toList();
    }
}
