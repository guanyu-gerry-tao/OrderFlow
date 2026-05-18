package com.orderflow.events;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes order events to Kafka or a Kafka-compatible broker.
 */
@Component
@ConditionalOnProperty(name = "orderflow.events.broker", havingValue = "kafka", matchIfMissing = true)
public class KafkaEventBroker implements EventBroker {

    private final KafkaTemplate<String, OrderEventMessage> kafkaTemplate;
    private final String topic;

    /**
     * Creates a Kafka-backed event broker.
     *
     * @param kafkaTemplate Kafka template
     * @param topic order event topic
     */
    public KafkaEventBroker(
            KafkaTemplate<String, OrderEventMessage> kafkaTemplate,
            @Value("${orderflow.events.topic:orderflow.order-events}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    /**
     * Publishes one message to Kafka.
     *
     * @param message order event message
     */
    @Override
    public void publish(OrderEventMessage message) {
        kafkaTemplate.send(topic, message.orderId().toString(), message).join();
    }
}
