package com.neueda.leap.team.market;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One trading day's raw closing price, in the symbol's quote currency. */
public record DailyClose(LocalDate date, BigDecimal close) {}
