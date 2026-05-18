package com.orderflow.operations;

import java.time.Instant;
import java.util.Map;

/**
 * Summarizes backend workflow health for the operations console.
 *
 * @param generatedAt response generation time
 * @param backendStatus backend component status
 * @param databaseStatus database component status
 * @param eventMode active event processing mode
 * @param eventBroker configured event broker
 * @param kafkaStatus Kafka configuration status
 * @param orderCount total order count
 * @param inventorySkuCount total inventory SKU count
 * @param outboxCounts outbox event counts by status
 * @param dlqCount total DLQ count
 * @param openDlqCount open DLQ count
 * @param replayedDlqCount replayed DLQ count
 * @param retryCount total retry attempts recorded on outbox events
 */
public record OperationsHealthResponse(
        Instant generatedAt,
        String backendStatus,
        String databaseStatus,
        String eventMode,
        String eventBroker,
        String kafkaStatus,
        long orderCount,
        long inventorySkuCount,
        Map<String, Long> outboxCounts,
        long dlqCount,
        long openDlqCount,
        long replayedDlqCount,
        long retryCount
) {
}
