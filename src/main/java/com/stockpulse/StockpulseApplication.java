package com.stockpulse;

import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class StockpulseApplication {

    public static void main(String[] args) {
        configureDefaultTimezone();
        SpringApplication.run(StockpulseApplication.class, args);
    }

    private static void configureDefaultTimezone() {
        String configuredTimezone = System.getenv("APP_TIMEZONE");
        if (configuredTimezone == null || configuredTimezone.isBlank()) {
            configuredTimezone = System.getProperty("app.timezone", "Asia/Kolkata");
        }

        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(configuredTimezone)));
    }
}
