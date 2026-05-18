package com.orderflow.dlq;

import com.orderflow.audit.OrderAuditService;
import com.orderflow.audit.OrderAuditLogRepository;
import com.orderflow.events.OrderEventConsumer;
import com.orderflow.order.OrderEntity;
import com.orderflow.order.OrderRepository;
import com.orderflow.outbox.OutboxEvent;
import com.orderflow.outbox.OutboxEventRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Requeues DLQ events for manual recovery.
 */
@Service
public class DeadLetterReplayService {

    private final DeadLetterEventRepository deadLetterEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderRepository orderRepository;
    private final OrderAuditService orderAuditService;
    private final OrderAuditLogRepository orderAuditLogRepository;
    private final OrderEventConsumer orderEventConsumer;

    /**
     * Creates the DLQ replay service.
     *
     * @param deadLetterEventRepository DLQ repository
     * @param outboxEventRepository outbox repository
     * @param orderRepository order repository
     * @param orderAuditService audit service
     * @param orderAuditLogRepository audit repository
     * @param orderEventConsumer event consumer
     */
    public DeadLetterReplayService(
            DeadLetterEventRepository deadLetterEventRepository,
            OutboxEventRepository outboxEventRepository,
            OrderRepository orderRepository,
            OrderAuditService orderAuditService,
            OrderAuditLogRepository orderAuditLogRepository,
            OrderEventConsumer orderEventConsumer
    ) {
        this.deadLetterEventRepository = deadLetterEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.orderRepository = orderRepository;
        this.orderAuditService = orderAuditService;
        this.orderAuditLogRepository = orderAuditLogRepository;
        this.orderEventConsumer = orderEventConsumer;
    }

    /**
     * Requeues a DLQ event and processes it once immediately.
     *
     * @param deadLetterEventId DLQ id
     */
    @Transactional
    public void retry(UUID deadLetterEventId) {
        UUID outboxEventId = requeue(deadLetterEventId);
        orderEventConsumer.processEventById(outboxEventId);
    }

    private UUID requeue(UUID deadLetterEventId) {
        DeadLetterEvent deadLetterEvent = deadLetterEventRepository.findById(deadLetterEventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DLQ event not found"));
        OutboxEvent outboxEvent = outboxEventRepository.findById(deadLetterEvent.getOutboxEventId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Outbox event not found"));
        OrderEntity order = orderRepository.findById(deadLetterEvent.getAggregateId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        // Leave an operator-visible audit event before replaying the failed event.
        orderAuditService.record(
                order.getId(),
                nextSequenceNumber(order.getId()),
                order.getStatus(),
                order.getStatus(),
                "Manual retry replayed DLQ event"
        );
        deadLetterEvent.markReplayed();
        outboxEvent.requeueForManualRetry();
        return outboxEvent.getId();
    }

    private int nextSequenceNumber(UUID orderId) {
        return orderAuditLogRepository.findByOrderIdOrderBySequenceNumberAsc(orderId).size() + 1;
    }
}
