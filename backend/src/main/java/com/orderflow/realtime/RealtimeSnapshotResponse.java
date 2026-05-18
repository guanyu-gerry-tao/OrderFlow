package com.orderflow.realtime;

import com.orderflow.operations.OperationsHealthResponse;
import java.time.Instant;

/**
 * Carries a live operations snapshot over Server-Sent Events.
 *
 * @param generatedAt snapshot generation time
 * @param health current operations health
 */
public record RealtimeSnapshotResponse(
        Instant generatedAt,
        OperationsHealthResponse health
) {
}
