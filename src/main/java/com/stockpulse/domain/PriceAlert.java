package com.stockpulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_alerts")
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 16)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PriceAlertDirection direction;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal targetPrice;

    @Column(nullable = false)
    private boolean triggered;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant triggeredAt;

    protected PriceAlert() {
    }

    public PriceAlert(AppUser user, String symbol, PriceAlertDirection direction, BigDecimal targetPrice) {
        this.user = user;
        this.symbol = symbol;
        this.direction = direction;
        this.targetPrice = targetPrice;
        this.triggered = false;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getSymbol() {
        return symbol;
    }

    public PriceAlertDirection getDirection() {
        return direction;
    }

    public BigDecimal getTargetPrice() {
        return targetPrice;
    }

    public boolean isTriggered() {
        return triggered;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public void markTriggered() {
        triggered = true;
        triggeredAt = Instant.now();
    }
}
