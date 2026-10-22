package com.neueda.leap.team.service;

import com.neueda.leap.team.dto.ValueHistoryDto;
import com.neueda.leap.team.market.ChartRange;
import com.neueda.leap.team.market.DailyCloseCache;
import com.neueda.leap.team.repository.AccountQueryRepository;
import com.neueda.leap.team.repository.AccountQueryRepository.Instrument;
import com.neueda.leap.team.repository.AccountQueryRepository.ValueEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rebuilds end-of-day account value by starting from today's cash and holdings and undoing trades and
 * completed cash transactions newest-first, then pricing each day's holdings at that day's close.
 * Days are UTC calendar days; weekends and holidays reuse the last close.
 */
@Service
@PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
public class ValueHistoryService {
    private record AccountState(BigDecimal cash, List<Instrument> instruments, List<ValueEvent> events, LocalDate first) {}
    private record Snapshot(LocalDate date, BigDecimal cash, Map<Long, BigDecimal> quantities) {}

    private final AccountQueryRepository queries;
    private final AccountAccessService access;
    private final DailyCloseCache closes;
    private final TransactionTemplate snapshotTx;

    public ValueHistoryService(AccountQueryRepository queries, AccountAccessService access, DailyCloseCache closes,
            PlatformTransactionManager transactions) {
        this.queries = queries; this.access = access; this.closes = closes;
        this.snapshotTx = new TransactionTemplate(transactions);
        snapshotTx.setReadOnly(true);
        snapshotTx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public ValueHistoryDto history(long accountId, String rangeCode) {
        ChartRange range = ChartRange.parse(rangeCode);
        LocalDate today = closes.today();
        LocalDate to = today.minusDays(1);
        // 1D has a single point: yesterday's close, the baseline for today's live value
        LocalDate from = range == ChartRange.D1 ? to : range.start(today);

        // Read balance, holdings and events in one snapshot so they agree; price lookups happen outside it
        AccountState state = snapshotTx.execute(status -> {
            access.readable(accountId);
            var account = queries.account(accountId).orElseThrow();
            LocalDate opened = LocalDate.ofInstant(account.createdDate(), ZoneOffset.UTC);
            LocalDate start = opened.isAfter(from) ? opened : from;
            return new AccountState(account.balance().cashBalance(), queries.instruments(accountId),
                    queries.valueEventsSince(accountId, endOf(start)), start);
        });
        BigDecimal cash = state.cash();
        List<Instrument> instruments = state.instruments();
        List<ValueEvent> events = state.events();
        LocalDate first = state.first();
        if (first.isAfter(to)) return new ValueHistoryDto(accountId, range.code(), List.of(), true);

        // Rewind from now to the end of each day, newest day first
        Map<Long, BigDecimal> quantities = new HashMap<>();
        instruments.forEach(i -> quantities.put(i.securityId(), i.quantity()));
        List<Snapshot> snapshots = new ArrayList<>();
        int next = 0;
        for (LocalDate day = to; !day.isBefore(first); day = day.minusDays(1)) {
            Instant end = endOf(day);
            for (; next < events.size() && !events.get(next).at().isBefore(end); next++) {
                ValueEvent event = events.get(next);
                cash = cash.subtract(event.cashChange());
                if (event.securityId() != null) quantities.merge(event.securityId(), event.quantityChange().negate(), BigDecimal::add);
            }
            snapshots.add(new Snapshot(day, cash, Map.copyOf(quantities)));
        }
        Collections.reverse(snapshots);

        // Price each day; only symbols actually held in the range are looked up
        Map<Long, Instrument> byId = new HashMap<>();
        instruments.forEach(i -> byId.put(i.securityId(), i));
        boolean complete = true;
        List<ValueHistoryDto.Point> points = new ArrayList<>();
        for (Snapshot snapshot : snapshots) {
            BigDecimal value = snapshot.cash();
            for (var held : snapshot.quantities().entrySet()) {
                if (held.getValue().signum() == 0) continue;
                Instrument instrument = byId.get(held.getKey());
                BigDecimal usdPrice = usdClose(instrument, snapshot.date());
                if (usdPrice == null) {
                    complete = false;
                    usdPrice = instrument.fallbackUsdPrice() == null ? BigDecimal.ZERO : instrument.fallbackUsdPrice();
                }
                value = value.add(held.getValue().multiply(usdPrice));
            }
            points.add(new ValueHistoryDto.Point(snapshot.date(), value.setScale(2, RoundingMode.HALF_UP)));
        }
        return new ValueHistoryDto(accountId, range.code(), points, complete);
    }

    // The latest close on or before the day, converted with the FX rate stored on the account's last fill
    private BigDecimal usdClose(Instrument instrument, LocalDate day) {
        BigDecimal fx = "USD".equals(instrument.quoteCurrency()) ? BigDecimal.ONE : instrument.fxRateToUsd();
        if (fx == null) return null;
        var history = closes.closes(instrument.ticker());
        var close = history == null ? null : history.floorEntry(day);
        return close == null ? null : close.getValue().multiply(fx);
    }

    private static Instant endOf(LocalDate day) { return day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(); }
}
