package com.orderflow.operations;

import com.orderflow.dlq.DeadLetterEventRepository;
import com.orderflow.dlq.DeadLetterStatus;
import com.orderflow.inventory.InventoryItemRepository;
import com.orderflow.order.OrderRepository;
import com.orderflow.outbox.EventMode;
import com.orderflow.outbox.OutboxEvent;
import com.orderflow.outbox.OutboxEventRepository;
import com.orderflow.outbox.OutboxEventStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Builds the service health read model used by the operations console.
 */
@Service
public class OperationsHealthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperationsHealthService.class);

    private final OrderRepository orderRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final DeadLetterEventRepository deadLetterEventRepository;
    private final EventMode eventMode;
    private final String eventBroker;

    /**
     * Creates the operations health service.
     *
     * @param orderRepository order repository
     * @param inventoryItemRepository inventory repository
     * @param outboxEventRepository outbox repository
     * @param deadLetterEventRepository DLQ repository
     * @param eventMode active event mode
     * @param eventBroker configured broker name
     */
    public OperationsHealthService(
            OrderRepository orderRepository,
            InventoryItemRepository inventoryItemRepository,
            OutboxEventRepository outboxEventRepository,
            DeadLetterEventRepository deadLetterEventRepository,
            EventMode eventMode,
            @Value("${orderflow.events.broker:kafka}") String eventBroker
    ) {
        this.orderRepository = orderRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.deadLetterEventRepository = deadLetterEventRepository;
        this.eventMode = eventMode;
        this.eventBroker = eventBroker;
    }

    /**
     * Returns the current health snapshot.
     *
     * @return operations health response
     */
    public OperationsHealthResponse getHealth() {
        try {
            Map<String, Long> outboxCounts = emptyOutboxCounts();
            for (OutboxEventStatus status : OutboxEventStatus.values()) {
                outboxCounts.put(status.name(), outboxEventRepository.countByStatus(status));
            }

            long retryCount = outboxEventRepository.findAll()
                    .stream()
                    .mapToLong(OutboxEvent::getRetryCount)
                    .sum();
            long openDlqCount = deadLetterEventRepository.countByStatus(DeadLetterStatus.OPEN);
            long replayedDlqCount = deadLetterEventRepository.countByStatus(DeadLetterStatus.REPLAYED);

            return new OperationsHealthResponse(
                    Instant.now(),
                    "UP",
                    "UP",
                    eventMode.getModeName(),
                    eventBroker,
                    kafkaStatus(),
                    orderRepository.count(),
                    inventoryItemRepository.count(),
                    outboxCounts,
                    deadLetterEventRepository.count(),
                    openDlqCount,
                    replayedDlqCount,
                    retryCount
            );
        } catch (DataAccessException ignored) {
            LOGGER.warn("Operations health degraded because database counters could not be loaded", ignored);
            return new OperationsHealthResponse(
                    Instant.now(),
                    "DEGRADED",
                    "DOWN",
                    eventMode.getModeName(),
                    eventBroker,
                    kafkaStatus(),
                    0,
                    0,
                    emptyOutboxCounts(),
                    0,
                    0,
                    0,
                    0
            );
        }
    }

    private Map<String, Long> emptyOutboxCounts() {
        Map<String, Long> outboxCounts = new LinkedHashMap<>();
        for (OutboxEventStatus status : OutboxEventStatus.values()) {
            outboxCounts.put(status.name(), 0L);
        }

        return outboxCounts;
    }

    private String kafkaStatus() {
        if (!eventMode.isOutboxKafka()) {
            return "NOT_USED";
        }
        if ("kafka".equalsIgnoreCase(eventBroker)) {
            return "CONFIGURED";
        }

        return "RECORDING_BROKER";
    }
}
