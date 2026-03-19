package com.stockpulse.repository;

import com.stockpulse.domain.Portfolio;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Portfolio> findByOwnerEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"owner"})
    Optional<Portfolio> findWithOwnerByOwnerEmailIgnoreCase(String email);
}
