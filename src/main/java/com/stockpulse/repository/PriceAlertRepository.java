package com.stockpulse.repository;

import com.stockpulse.domain.PriceAlert;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    List<PriceAlert> findByUserEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    List<PriceAlert> findBySymbolIgnoreCaseAndTriggeredFalse(String symbol);
}
