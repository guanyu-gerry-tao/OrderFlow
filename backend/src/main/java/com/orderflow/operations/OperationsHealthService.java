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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the service health read model used by the operations console.
 */
@Service
public class OperationsHealthService {

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
    @Transactional(readOnly = true)
    public OperationsHealthResponse getHealth() {
        Map<String, Long> outboxCounts = new LinkedHashMap<>();
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
