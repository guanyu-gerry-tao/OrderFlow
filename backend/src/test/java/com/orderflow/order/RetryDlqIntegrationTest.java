package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.audit.OrderAuditLogRepository;
import com.orderflow.dlq.DeadLetterEvent;
import com.orderflow.dlq.DeadLetterEventRepository;
import com.orderflow.dlq.DeadLetterStatus;
import com.orderflow.events.OrderEventConsumer;
import com.orderflow.events.OrderEventRetryScheduler;
import com.orderflow.events.OrderEventType;
import com.orderflow.events.RecordingEventBroker;
import com.orderflow.failure.FailureInjectionService;
import com.orderflow.idempotency.IdempotencyRecordRepository;
import com.orderflow.inventory.InventoryItemRepository;
import com.orderflow.inventory.SeedInventoryRequest;
import com.orderflow.outbox.OutboxEvent;
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
class RetryDlqIntegrationTest {

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
        registry.add("orderflow.events.retry.initial-backoff", () -> "0ms");
        registry.add("orderflow.events.retry.max-attempts", () -> "2");
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

    @Autowired
    private OrderEventRetryScheduler orderEventRetryScheduler;

    @Autowired
    private FailureInjectionService failureInjectionService;

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
        failureInjectionService.clear();
    }

    @Test
    void publishFailureRetriesThenMovesEventToDlq() {
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest("SKU-PUBLISH-FAIL", 5), Void.class);
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                "customer-publish-fail",
                List.of(new CreateOrderItemRequest("SKU-PUBLISH-FAIL", 1))
        );
        restTemplate.postForEntity("/api/orders", createOrderRequest, OrderResponse.class);
        recordingEventBroker.failNextPublish("Injected publish failure");

        int firstPublishAttempt = outboxPublisher.publishDueEvents();
        OutboxEvent retriedEvent = outboxEventRepository.findAll().get(0);

        assertThat(firstPublishAttempt).isZero();
        assertThat(retriedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(retriedEvent.getRetryCount()).isEqualTo(1);
        assertThat(retriedEvent.getNextAttemptAt()).isNotNull();
        assertThat(retriedEvent.getLastError()).contains("Injected publish failure");
        assertThat(recordingEventBroker.publishedMessages()).isEmpty();

        recordingEventBroker.failNextPublish("Injected publish failure again");

        int secondPublishAttempt = outboxPublisher.publishDueEvents();
        OutboxEvent deadLetteredEvent = outboxEventRepository.findAll().get(0);

        assertThat(secondPublishAttempt).isZero();
        assertThat(deadLetteredEvent.getStatus()).isEqualTo(OutboxEventStatus.DLQ);
        assertThat(deadLetteredEvent.getRetryCount()).isEqualTo(2);
        assertThat(deadLetterEventRepository.findAll()).hasSize(1);
        assertThat(deadLetterEventRepository.findAll().get(0).getLastError())
                .contains("Injected publish failure again");
    }

    @Test
    void consumerCrashRecordsRetryMetadataWithoutSilentLoss() {
        OrderResponse createResponse = createInventoryReservedOrder("SKU-CONSUMER-CRASH");
        OutboxEvent paymentEvent = publishInventoryReservedEvent(createResponse.orderId());
        failureInjectionService.failConsumerOnce(paymentEvent.getId(), "Injected consumer crash");

        int processedEvents = orderEventConsumer.processDuePublishedEvents();
        OutboxEvent retriedEvent = outboxEventRepository.findById(paymentEvent.getId()).get();

        assertThat(processedEvents).isZero();
        assertThat(retriedEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(retriedEvent.getRetryCount()).isEqualTo(1);
        assertThat(retriedEvent.getLastError()).contains("Injected consumer crash");
        assertThat(deadLetterEventRepository.count()).isZero();
    }

    @Test
    void retrySchedulerReprocessesDueConsumerFailures() {
        OrderResponse createResponse = createInventoryReservedOrder("SKU-CONSUMER-RETRY");
        OutboxEvent paymentEvent = publishInventoryReservedEvent(createResponse.orderId());
        failureInjectionService.failConsumerOnce(paymentEvent.getId(), "Injected consumer crash");
        orderEventConsumer.processDuePublishedEvents();

        int retriedEvents = orderEventRetryScheduler.retryDueEvents();

        assertThat(retriedEvents).isEqualTo(1);
        assertThat(outboxEventRepository.findById(paymentEvent.getId()).get().getStatus())
                .isEqualTo(OutboxEventStatus.PROCESSED);
        assertThat(orderRepository.findById(createResponse.orderId()).get().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
        assertThat(deadLetterEventRepository.count()).isZero();
    }

    @Test
    void paymentTimeoutMovesToDlqAndManualRetryReplaysEventWithAuditLog() {
        OrderResponse createResponse = createInventoryReservedOrder("SKU-PAYMENT-TIMEOUT");
        OutboxEvent paymentEvent = publishInventoryReservedEvent(createResponse.orderId());
        failureInjectionService.failPaymentAttempts(createResponse.orderId(), 2);

        orderEventConsumer.processDuePublishedEvents();
        orderEventConsumer.processDuePublishedEvents();

        assertThat(deadLetterEventRepository.findAll()).hasSize(1);
        DeadLetterEvent deadLetterEvent = deadLetterEventRepository.findAll().get(0);
        assertThat(deadLetterEvent.getLastError()).contains("Injected payment timeout");
        assertThat(orderRepository.findById(createResponse.orderId()).get().getStatus())
                .isEqualTo(OrderStatus.INVENTORY_RESERVED);

        failureInjectionService.clear();
        ResponseEntity<Void> retryResponse = restTemplate.postForEntity(
                "/api/dlq/{deadLetterEventId}/retry",
                null,
                Void.class,
                deadLetterEvent.getId()
        );

        assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(orderRepository.findById(createResponse.orderId()).get().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
        assertThat(paymentAttemptRepository.findByOrderId(createResponse.orderId())).hasSize(1);
        assertThat(orderAuditLogRepository.findByOrderIdOrderBySequenceNumberAsc(createResponse.orderId()))
                .extracting(auditLog -> auditLog.getMessage())
                .contains("Manual retry replayed DLQ event", "Payment authorized", "Order completed");
    }

    @Test
    void manualRetryKeepsDlqOpenWhenReplayFailsAgain() {
        OrderResponse createResponse = createInventoryReservedOrder("SKU-MANUAL-RETRY-FAILS");
        publishInventoryReservedEvent(createResponse.orderId());
        failureInjectionService.failPaymentAttempts(createResponse.orderId(), 2);
        orderEventConsumer.processDuePublishedEvents();
        orderEventConsumer.processDuePublishedEvents();
        DeadLetterEvent deadLetterEvent = deadLetterEventRepository.findAll().get(0);

        failureInjectionService.failPaymentAttempts(createResponse.orderId(), 1);
        ResponseEntity<Void> retryResponse = restTemplate.postForEntity(
                "/api/dlq/{deadLetterEventId}/retry",
                null,
                Void.class,
                deadLetterEvent.getId()
        );

        assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(deadLetterEventRepository.findById(deadLetterEvent.getId()).get().getStatus())
                .isEqualTo(DeadLetterStatus.OPEN);
        assertThat(orderRepository.findById(createResponse.orderId()).get().getStatus())
                .isEqualTo(OrderStatus.INVENTORY_RESERVED);
    }

    private OrderResponse createInventoryReservedOrder(String sku) {
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest(sku, 5), Void.class);
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                "customer-" + sku.toLowerCase(),
                List.of(new CreateOrderItemRequest(sku, 1))
        );
        OrderResponse createResponse = restTemplate.postForObject("/api/orders", createOrderRequest, OrderResponse.class);
        outboxPublisher.publishDueEvents();
        orderEventConsumer.processDuePublishedEvents();
        assertThat(orderRepository.findById(createResponse.orderId()).get().getStatus())
                .isEqualTo(OrderStatus.INVENTORY_RESERVED);
        return createResponse;
    }

    private OutboxEvent publishInventoryReservedEvent(java.util.UUID orderId) {
        OutboxEvent paymentEvent = outboxEventRepository.findByEventType(OrderEventType.INVENTORY_RESERVED).get(0);
        outboxPublisher.publishDueEvents();
        assertThat(outboxEventRepository.findById(paymentEvent.getId()).get().getStatus())
                .isEqualTo(OutboxEventStatus.PUBLISHED);
        return outboxEventRepository.findById(paymentEvent.getId()).get();
    }
}
