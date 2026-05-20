package com.orderflow.events;

import com.orderflow.config.ConditionalOnRuntimeRole;
import com.orderflow.config.RuntimeRole;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Receives Kafka-delivered order events and delegates to the database-backed consumer.
 */
@Component
@ConditionalOnProperty(name = "orderflow.events.broker", havingValue = "kafka", matchIfMissing = true)
@ConditionalOnRuntimeRole(RuntimeRole.WORKER)
public class KafkaOrderEventListener {

    private final OrderEventConsumer orderEventConsumer;

    /**
     * Creates the Kafka listener.
     *
     * @param orderEventConsumer order event consumer
     */
    public KafkaOrderEventListener(OrderEventConsumer orderEventConsumer) {
        this.orderEventConsumer = orderEventConsumer;
    }

    /**
     * Handles one Kafka message.
     *
     * @param message order event message
     */
    @KafkaListener(
            topics = "${orderflow.events.topic:orderflow.order-events}",
            groupId = "orderflow-backend"
    )
    public void handle(OrderEventMessage message) {
        orderEventConsumer.processEventById(message.eventId());
    }
}
