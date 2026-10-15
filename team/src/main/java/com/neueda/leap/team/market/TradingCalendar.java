package com.neueda.leap.team.market;

import com.neueda.leap.team.config.TradingCalendarProperties;
import com.neueda.leap.team.order.SecurityDto;
import java.time.*;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Teaching schedule: regular sessions, DST-aware zones, configurable full-day closures. */
@Component
public class TradingCalendar {
    private final TradingCalendarProperties properties;
    public TradingCalendar(TradingCalendarProperties properties) { this.properties=properties; }
    public boolean isOpen(SecurityDto s, String providerState, Instant now) {
        if(s.assetType().equals("CRYPTO"))return true; // 24/7; no stock-exchange session.
        if(s.assetType().equals("FOREX")) {
            // Frankfurter always reports 'closed': it describes a daily reference, not a session.
            var ny=now.atZone(ZoneId.of("America/New_York"));
            return switch(ny.getDayOfWeek()) {
                case SATURDAY -> false;
                case SUNDAY -> !ny.toLocalTime().isBefore(LocalTime.of(17,0));
                case FRIDAY -> ny.toLocalTime().isBefore(LocalTime.of(17,0));
                default -> true;
            };
        }
        if(Set.of("closed","pre","post").contains(providerState))return false;
        if(!providerState.equals("open") && !properties.allowUnknownEquityState())return false;
        boolean india=Set.of("NSE","BSE").contains(s.exchange());
        boolean us=Set.of("US","NYSE","NASDAQ","NYSEARCA","AMEX","ARCA","BATS").contains(s.exchange());
        if(!india&&!us)return providerState.equals("open"); // New venues need an explicit provider session.
        var local=now.atZone(ZoneId.of(india?"Asia/Kolkata":"America/New_York"));
        if(local.getDayOfWeek()==DayOfWeek.SATURDAY||local.getDayOfWeek()==DayOfWeek.SUNDAY)return false;
        if((india?properties.indiaClosedDates():properties.usClosedDates()).contains(local.toLocalDate()))return false;
        LocalTime open=india?LocalTime.of(9,15):LocalTime.of(9,30);
        LocalTime close=india?LocalTime.of(15,30):LocalTime.of(16,0);
        return !local.toLocalTime().isBefore(open)&&local.toLocalTime().isBefore(close);
    }
}
