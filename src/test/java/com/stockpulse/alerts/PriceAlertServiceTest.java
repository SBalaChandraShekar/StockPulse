package com.stockpulse.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockpulse.alerts.dto.CreatePriceAlertRequest;
import com.stockpulse.domain.AppUser;
import com.stockpulse.domain.PriceAlert;
import com.stockpulse.domain.PriceAlertDirection;
import com.stockpulse.domain.UserRole;
import com.stockpulse.market.MarketQuote;
import com.stockpulse.repository.AppUserRepository;
import com.stockpulse.repository.PriceAlertRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceAlertServiceTest {

    @Mock
    private PriceAlertRepository priceAlertRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AlertEmailService alertEmailService;

    @InjectMocks
    private PriceAlertService priceAlertService;

    @Test
    void createAlertNormalizesSymbolAndPersistsIt() {
        AppUser user = new AppUser("Bala", "bala@example.com", "hash", UserRole.ROLE_USER);
        when(appUserRepository.findByEmailIgnoreCase("bala@example.com")).thenReturn(Optional.of(user));
        when(priceAlertRepository.save(org.mockito.ArgumentMatchers.any(PriceAlert.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        priceAlertService.createAlert("bala@example.com",
                new CreatePriceAlertRequest("aapl", PriceAlertDirection.ABOVE, new BigDecimal("200")));

        ArgumentCaptor<PriceAlert> captor = ArgumentCaptor.forClass(PriceAlert.class);
        verify(priceAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getSymbol()).isEqualTo("AAPL");
        assertThat(captor.getValue().getTargetPrice()).isEqualByComparingTo("200.0000");
    }

    @Test
    void evaluateAlertsTriggersMatchingAlertAndEmailsUser() {
        AppUser user = new AppUser("Bala", "bala@example.com", "hash", UserRole.ROLE_USER);
        PriceAlert alert = new PriceAlert(user, "AAPL", PriceAlertDirection.ABOVE, new BigDecimal("200.0000"));
        when(priceAlertRepository.findBySymbolIgnoreCaseAndTriggeredFalse("AAPL")).thenReturn(List.of(alert));

        int triggeredCount = priceAlertService.evaluateAlerts(
                "AAPL",
                new MarketQuote("AAPL", new BigDecimal("210.0000"), "mock", Instant.now()));

        assertThat(triggeredCount).isEqualTo(1);
        assertThat(alert.isTriggered()).isTrue();
        verify(alertEmailService).sendAlert(org.mockito.ArgumentMatchers.eq(alert), org.mockito.ArgumentMatchers.any(MarketQuote.class));
    }
}
