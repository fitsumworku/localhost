package com.neueda.leap.team.reporting;

import java.time.Instant;
import java.util.Map;

/** Snapshot fields are durable; attempt/running fields describe this app instance only. */
public record EtlStatus(int schemaVersion, boolean initialized, Instant sourceSnapshotAt,
                        Instant lastSuccessfulAt, Long durationMs, long refreshCount,
                        Map<String, Long> rowCounts, boolean refreshRunningOnThisInstance,
                        Instant lastAttemptAt, String lastAttemptOutcome) {}
