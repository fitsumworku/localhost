package com.neueda.leap.team.reporting;

import com.neueda.leap.team.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = {"app.reporting.enabled", "app.reporting.etl.enabled"}, havingValue = "true")
public class ReportingEtlScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReportingEtlScheduler.class);
    private final ReportingEtlService etl;

    public ReportingEtlScheduler(ReportingEtlService etl) { this.etl = etl; }

    @Scheduled(fixedDelayString = "${app.reporting.etl.delay-ms:60000}",
            initialDelayString = "${app.reporting.etl.initial-delay-ms:10000}")
    public void refreshSnapshot() {
        try {
            etl.refresh();
        } catch (ApiException failure) {
            // OLTP startup and order processing remain available if reporting is
            // offline. The next scheduled run retries; the last snapshot persists.
            log.warn("Scheduled reporting refresh did not complete: {}", failure.code());
        }
    }
}
