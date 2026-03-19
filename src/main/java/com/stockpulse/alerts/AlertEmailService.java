package com.stockpulse.alerts;

import com.stockpulse.domain.PriceAlert;
import com.stockpulse.market.MarketQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AlertEmailService {

    private static final Logger log = LoggerFactory.getLogger(AlertEmailService.class);

    private final JavaMailSender mailSender;
    private final AlertMailProperties alertMailProperties;

    public AlertEmailService(JavaMailSender mailSender, AlertMailProperties alertMailProperties) {
        this.mailSender = mailSender;
        this.alertMailProperties = alertMailProperties;
    }

    public void sendAlert(PriceAlert alert, MarketQuote quote) {
        if (!alertMailProperties.enabled()) {
            log.info("Email alerts disabled. Would have emailed {} about {} {} {}",
                    alert.getUser().getEmail(),
                    alert.getSymbol(),
                    alert.getDirection(),
                    alert.getTargetPrice());
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(alertMailProperties.from());
        message.setTo(alert.getUser().getEmail());
        message.setSubject("StockPulse alert: " + alert.getSymbol() + " crossed your target");
        message.setText("""
                Hi,

                Your StockPulse alert has triggered.

                Symbol: %s
                Direction: %s
                Target price: %s
                Current price: %s
                Source: %s

                Regards,
                StockPulse
                """.formatted(
                alert.getSymbol(),
                alert.getDirection(),
                alert.getTargetPrice(),
                quote.price(),
                quote.source()));
        try {
            mailSender.send(message);
        } catch (RuntimeException ex) {
            log.warn("Failed to send price alert email to {}: {}", alert.getUser().getEmail(), ex.getMessage());
        }
    }
}
