package com.neueda.leap.team.reporting;

import com.neueda.leap.team.exception.ApiError;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Includes failures opening the read transaction, before the service method runs. */
@RestControllerAdvice(assignableTypes = ReportingController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "app.reporting", name = "enabled", havingValue = "true")
public class ReportingExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ReportingExceptionHandler.class);
    private final Clock clock;

    public ReportingExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ApiError> unavailable(Exception failure) {
        log.warn("Reporting request could not reach a usable OLAP snapshot ({})", failure.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(clock.instant(), 503,
                "REPORTING_UNAVAILABLE", "The reporting database is temporarily unavailable. Try again after it is initialized and reachable.", Map.of()));
    }
}
