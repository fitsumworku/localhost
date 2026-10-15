package com.neueda.leap.team.config;

import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.trading-calendar")
public record TradingCalendarProperties(@DefaultValue("true") boolean allowUnknownEquityState,
        @DefaultValue List<LocalDate> usClosedDates, @DefaultValue List<LocalDate> indiaClosedDates) {}
