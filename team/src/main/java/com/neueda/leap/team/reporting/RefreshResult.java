package com.neueda.leap.team.reporting;

import java.time.Instant;
import java.util.Map;

/** Returned only after the complete replacement snapshot has committed. */
public record RefreshResult(Instant sourceSnapshotAt, Instant completedAt,
                            long durationMs, Map<String, Long> rowCounts) {}
