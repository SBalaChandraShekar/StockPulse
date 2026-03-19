package com.stockpulse.repository;

import com.stockpulse.domain.WatchlistItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistRepository extends JpaRepository<WatchlistItem, Long> {

    Optional<WatchlistItem> findBySymbolIgnoreCase(String symbol);
}
