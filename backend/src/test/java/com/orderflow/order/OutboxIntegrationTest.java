package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.audit.OrderAuditLogRepository;
import com.orderflow.dlq.DeadLetterEventRepository;
import com.orderflow.events.OrderEventConsumer;
import com.orderflow.events.OrderEventType;
import com.orderflow.events.RecordingEventBroker;
import com.orderflow.idempotency.IdempotencyRecordRepository;
import com.orderflow.inventory.InventoryItemRepository;
import com.orderflow.inventory.SeedInventoryRequest;
import com.orderflow.outbox.OutboxEventRepository;
import com.orderflow.outbox.OutboxEventStatus;
import com.orderflow.outbox.OutboxPublisher;
import com.orderflow.payment.PaymentAttemptRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OutboxIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> "1");
        registry.add("spring.data.redis.timeout", () -> "100ms");
        registry.add("orderflow.events.mode", () -> "outbox-kafka");
        registry.add("orderflow.events.broker", () -> "recording");
        registry.add("orderflow.events.publisher-initial-delay", () -> "600000");
        registry.add("orderflow.events.publisher-interval", () -> "600000");
        registry.add("orderflow.events.consumer-retry-initial-delay", () -> "600000");
        registry.add("orderflow.events.consumer-retry-interval", () -> "600000");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private OrderAuditLogRepository orderAuditLogRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private DeadLetterEventRepository deadLetterEventRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private RecordingEventBroker recordingEventBroker;

    @Autowired
    private OrderEventConsumer orderEventConsumer;

    @BeforeEach
    void clearDatabase() {
        deadLetterEventRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        orderAuditLogRepository.deleteAll();
        paymentAttemptRepository.deleteAll();
        outboxEventRepository.deleteAll();
        orderRepository.deleteAll();
        inventoryItemRepository.deleteAll();
        recordingEventBroker.clear();
    }

    @Test
    void creatingOrderWritesTransactionalOutboxEventAndPublisherMarksItPublished() {
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest("SKU-OUTBOX", 10), Void.class);
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                "customer-outbox",
                List.of(new CreateOrderItemRequest("SKU-OUTBOX", 2))
        );

        ResponseEntity<OrderResponse> createResponse = restTemplate.postForEntity(
                "/api/orders",
                createOrderRequest,
                OrderResponse.class
        );
        int publishedEvents = outboxPublisher.publishDueEvents();

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().status()).isEqualTo(OrderStatus.CREATED);
        assertThat(outboxEventRepository.findAll()).hasSize(1);
        assertThat(outboxEventRepository.findAll().get(0).getEventType()).isEqualTo(OrderEventType.ORDER_CREATED);
        assertThat(outboxEventRepository.findAll().get(0).getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(publishedEvents).isEqualTo(1);
        assertThat(recordingEventBroker.publishedMessages()).hasSize(1);
        assertThat(recordingEventBroker.publishedMessages().get(0).eventType()).isEqualTo(OrderEventType.ORDER_CREATED);
    }

    @Test
    void consumerProcessesOrderCreatedEventThroughInventoryReservation() {
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest("SKU-CONSUMER", 7), Void.class);
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                "customer-consumer",
                List.of(new CreateOrderItemRequest("SKU-CONSUMER", 3))
        );
        OrderResponse createResponse = restTemplate.postForObject("/api/orders", createOrderRequest, OrderResponse.class);
        outboxPublisher.publishDueEvents();

        int processedEvents = orderEventConsumer.processDuePublishedEvents();

        assertThat(processedEvents).isEqualTo(1);
        assertThat(orderRepository.findById(createResponse.orderId()).get().getStatus())
                .isEqualTo(OrderStatus.INVENTORY_RESERVED);
        assertThat(inventoryItemRepository.findBySku("SKU-CONSUMER").get().getAvailableQuantity()).isEqualTo(4);
        assertThat(outboxEventRepository.findByEventType(OrderEventType.INVENTORY_RESERVED)).hasSize(1);
        assertThat(orderAuditLogRepository.findByOrderIdOrderBySequenceNumberAsc(createResponse.orderId()))
                .extracting(auditLog -> auditLog.getToStatus())
                .containsExactly(OrderStatus.CREATED, OrderStatus.INVENTORY_RESERVED);
    }
}
