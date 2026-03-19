package com.stockpulse.repository;

import com.stockpulse.domain.Holding;
import com.stockpulse.domain.Portfolio;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByPortfolioIdOrderBySymbolAsc(Long portfolioId);

    Optional<Holding> findByPortfolioAndSymbolIgnoreCase(Portfolio portfolio, String symbol);

    @Query("select distinct upper(h.symbol) from Holding h where h.quantity > 0 order by upper(h.symbol)")
    List<String> findDistinctTrackedSymbols();
}
