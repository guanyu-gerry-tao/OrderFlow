package com.orderflow.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orderflow.events.OrderEventType;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Creates outbox records inside the same transaction as workflow state changes.
 */
@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Creates the outbox service.
     *
     * @param outboxEventRepository outbox repository
     * @param objectMapper JSON mapper
     */
    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Writes an order workflow event to the outbox.
     *
     * @param orderId order id
     * @param eventType event type
     * @return saved outbox event
     */
    public OutboxEvent enqueueOrderEvent(UUID orderId, OrderEventType eventType) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId.toString());
        payload.put("eventType", eventType.name());
        return outboxEventRepository.save(new OutboxEvent(orderId, eventType, payload.toString()));
    }
}
