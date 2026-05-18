package com.orderflow.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orderflow.audit.OrderAuditLogRepository;
import com.orderflow.dlq.DeadLetterEvent;
import com.orderflow.dlq.DeadLetterEventRepository;
import com.orderflow.events.OrderEventConsumer;
import com.orderflow.events.OrderEventRetryScheduler;
import com.orderflow.events.OrderEventType;
import com.orderflow.events.RecordingEventBroker;
import com.orderflow.failure.FailureInjectionService;
import com.orderflow.idempotency.IdempotencyRecordRepository;
import com.orderflow.inventory.InventoryItemRepository;
import com.orderflow.inventory.SeedInventoryRequest;
import com.orderflow.order.CreateOrderItemRequest;
import com.orderflow.order.CreateOrderRequest;
import com.orderflow.order.OrderRepository;
import com.orderflow.order.OrderResponse;
import com.orderflow.order.OrderStatus;
import com.orderflow.outbox.OutboxEvent;
import com.orderflow.outbox.OutboxEventRepository;
import com.orderflow.outbox.OutboxEventStatus;
import com.orderflow.outbox.OutboxPublisher;
import com.orderflow.payment.PaymentAttemptRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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

@Tag("async-benchmark")
@Testcontainers
@EnabledIfSystemProperty(named = "orderflow.async.benchmark.enabled", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AsyncReliabilityBenchmarkTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String mode = System.getProperty("orderflow.async.benchmark.mode", "outbox-kafka");
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> "1");
        registry.add("spring.data.redis.timeout", () -> "100ms");
        registry.add("orderflow.events.mode", () -> eventModeFor(mode));
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

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearDatabase() {
        clearState();
    }

    @Test
    void writesAsyncReliabilityBenchmarkResult() throws Exception {
        String mode = System.getProperty("orderflow.async.benchmark.mode", "outbox-kafka");
        int syntheticOrders = integerProperty("orderflow.async.benchmark.syntheticOrders", 50);
        ObjectNode benchmarkResult = objectMapper.createObjectNode();
        benchmarkResult.put("suite", "async-reliability");
        benchmarkResult.put("mode", mode);
        benchmarkResult.put("syntheticOrders", syntheticOrders);

        if ("direct".equalsIgnoreCase(eventModeFor(mode))) {
            benchmarkResult.set("workflow", runDirectWorkflowScenario(syntheticOrders));
            benchmarkResult.set("failureRecovery", baselineFailureRecoverySummary());
        } else {
            benchmarkResult.set("workflow", runOutboxWorkflowScenario(syntheticOrders));
            clearState();
            benchmarkResult.set("failureRecovery", runOutboxFailureRecoveryScenarios());
        }
        assertAsyncReliabilityInvariants(mode, benchmarkResult);

        Path outputDirectory = Path.of(System.getProperty(
                "orderflow.async.benchmark.outputDir",
                "benchmarks/results/full/async-reliability"
        ));
        BenchmarkReportWriter writer = new BenchmarkReportWriter(objectMapper);
        BenchmarkReportFiles reportFiles = writer.writeReport(
                outputDirectory,
                mode,
                mode,
                "Async Reliability Benchmark",
                benchmarkResult
        );

        assertThat(Files.exists(reportFiles.jsonPath())).isTrue();
        assertThat(Files.exists(reportFiles.markdownPath())).isTrue();
    }

    private ObjectNode runDirectWorkflowScenario(int syntheticOrders) {
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest("SKU-ASYNC-DIRECT", syntheticOrders + 10), Void.class);
        List<Long> latencies = new ArrayList<>();
        Instant startedAt = Instant.now();

        for (int index = 0; index < syntheticOrders; index++) {
            latencies.add(createOrderAndMeasureLatency("customer-direct-" + index, "SKU-ASYNC-DIRECT"));
        }

        long completedOrders = orderRepository.findAll()
                .stream()
                .filter(order -> order.getStatus() == OrderStatus.COMPLETED)
                .count();

        ObjectNode result = objectMapper.createObjectNode();
        result.put("attemptedOrders", syntheticOrders);
        result.put("completedOrders", completedOrders);
        result.put("durationMs", Duration.between(startedAt, Instant.now()).toMillis());
        result.put("p95CreateLatencyMs", percentile(latencies, 95));
        result.put("p99CreateLatencyMs", percentile(latencies, 99));
        result.put("outboxEvents", outboxEventRepository.count());
        result.put("dlqCount", deadLetterEventRepository.count());
        return result;
    }

    private ObjectNode runOutboxWorkflowScenario(int syntheticOrders) {
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest("SKU-ASYNC-OUTBOX", syntheticOrders + 10), Void.class);
        List<Long> latencies = new ArrayList<>();
        Instant startedAt = Instant.now();

        for (int index = 0; index < syntheticOrders; index++) {
            latencies.add(createOrderAndMeasureLatency("customer-outbox-" + index, "SKU-ASYNC-OUTBOX"));
        }

        int publishedEvents = 0;
        int processedEvents = 0;
        for (int iteration = 0; iteration < 4; iteration++) {
            publishedEvents = publishedEvents + outboxPublisher.publishDueEvents();
            processedEvents = processedEvents + orderEventConsumer.processDuePublishedEvents();
        }

        long completedOrders = orderRepository.findAll()
                .stream()
                .filter(order -> order.getStatus() == OrderStatus.COMPLETED)
                .count();

        ObjectNode result = objectMapper.createObjectNode();
        result.put("attemptedOrders", syntheticOrders);
        result.put("completedOrders", completedOrders);
        result.put("publishedEvents", publishedEvents);
        result.put("processedEvents", processedEvents);
        result.put("durationMs", Duration.between(startedAt, Instant.now()).toMillis());
        result.put("p95CreateLatencyMs", percentile(latencies, 95));
        result.put("p99CreateLatencyMs", percentile(latencies, 99));
        result.put("pendingOutboxEvents", outboxEventRepository.countByStatus(OutboxEventStatus.PENDING));
        result.put("dlqCount", deadLetterEventRepository.count());
        return result;
    }

    private ObjectNode baselineFailureRecoverySummary() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("retryCount", 0);
        result.put("dlqCount", 0);
        result.put("manualRetrySuccessCount", 0);
        result.put("note", "Direct baseline completes the workflow synchronously and does not exercise outbox retry or DLQ recovery.");
        return result;
    }

    private ObjectNode runOutboxFailureRecoveryScenarios() {
        ObjectNode result = objectMapper.createObjectNode();
        result.set("scenarioIds", scenarioIds("F06", "F09", "F11", "F13", "F14"));
        result.set("publishFailure", runPublishFailureScenario());
        result.set("consumerCrash", runConsumerCrashScenario());
        result.set("manualRetrySuccess", runManualRetrySuccessScenario());
        result.set("manualRetryFailure", runManualRetryFailureScenario());
        result.put("retryCount", outboxEventRepository.findAll()
                .stream()
                .mapToInt(OutboxEvent::getRetryCount)
                .sum());
        result.put("dlqCount", deadLetterEventRepository.count());
        return result;
    }

    private ObjectNode runPublishFailureScenario() {
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest("SKU-PUBLISH-BENCH", 5), Void.class);
        createOrderAndMeasureLatency("customer-publish-bench", "SKU-PUBLISH-BENCH");
        recordingEventBroker.failNextPublish("Benchmark publish failure");
        outboxPublisher.publishDueEvents();
        recordingEventBroker.failNextPublish("Benchmark publish failure again");
        outboxPublisher.publishDueEvents();

        ObjectNode result = objectMapper.createObjectNode();
        result.put("dlqEventsAfterScenario", deadLetterEventRepository.count());
        result.put("lastError", deadLetterEventRepository.findAll().get(0).getLastError());
        return result;
    }

    private ObjectNode runConsumerCrashScenario() {
        OrderResponse createResponse = createInventoryReservedOrder("SKU-CONSUMER-BENCH");
        OutboxEvent paymentEvent = publishInventoryReservedEvent(createResponse.orderId());
        failureInjectionService.failConsumerOnce(paymentEvent.getId(), "Benchmark consumer crash");

        orderEventConsumer.processDuePublishedEvents();
        OutboxEvent retriedEvent = outboxEventRepository.findById(paymentEvent.getId()).get();
        orderEventRetryScheduler.retryDueEvents();

        ObjectNode result = objectMapper.createObjectNode();
        result.put("retryCountAfterCrash", retriedEvent.getRetryCount());
        result.put("statusAfterRetry", outboxEventRepository.findById(paymentEvent.getId()).get().getStatus().name());
        return result;
    }

    private ObjectNode runManualRetrySuccessScenario() {
        OrderResponse createResponse = createInventoryReservedOrder("SKU-MANUAL-SUCCESS-BENCH");
        publishInventoryReservedEvent(createResponse.orderId());
        failureInjectionService.failPaymentAttempts(createResponse.orderId(), 2);
        orderEventConsumer.processDuePublishedEvents();
        orderEventConsumer.processDuePublishedEvents();
        DeadLetterEvent deadLetterEvent = newestDeadLetterEvent();

        failureInjectionService.clear();
        ResponseEntity<Void> retryResponse = restTemplate.postForEntity(
                "/api/dlq/{deadLetterEventId}/retry",
                null,
                Void.class,
                deadLetterEvent.getId()
        );

        ObjectNode result = objectMapper.createObjectNode();
        result.put("retryStatus", retryResponse.getStatusCode().value());
        result.put("orderStatus", orderRepository.findById(createResponse.orderId()).get().getStatus().name());
        return result;
    }

    private ObjectNode runManualRetryFailureScenario() {
        OrderResponse createResponse = createInventoryReservedOrder("SKU-MANUAL-FAIL-BENCH");
        publishInventoryReservedEvent(createResponse.orderId());
        failureInjectionService.failPaymentAttempts(createResponse.orderId(), 2);
        orderEventConsumer.processDuePublishedEvents();
        orderEventConsumer.processDuePublishedEvents();
        DeadLetterEvent deadLetterEvent = newestDeadLetterEvent();

        failureInjectionService.failPaymentAttempts(createResponse.orderId(), 1);
        ResponseEntity<Void> retryResponse = restTemplate.postForEntity(
                "/api/dlq/{deadLetterEventId}/retry",
                null,
                Void.class,
                deadLetterEvent.getId()
        );

        ObjectNode result = objectMapper.createObjectNode();
        result.put("retryStatus", retryResponse.getStatusCode().value());
        result.put("orderStatus", orderRepository.findById(createResponse.orderId()).get().getStatus().name());
        return result;
    }

    private OrderResponse createInventoryReservedOrder(String sku) {
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest(sku, 5), Void.class);
        CreateOrderRequest request = createOrderRequest("customer-" + sku.toLowerCase(), sku);
        OrderResponse response = restTemplate.postForObject("/api/orders", request, OrderResponse.class);
        outboxPublisher.publishDueEvents();
        orderEventConsumer.processDuePublishedEvents();
        return response;
    }

    private OutboxEvent publishInventoryReservedEvent(UUID orderId) {
        OutboxEvent paymentEvent = outboxEventRepository.findByEventType(OrderEventType.INVENTORY_RESERVED)
                .stream()
                .filter(event -> event.getAggregateId().equals(orderId))
                .findFirst()
                .orElseThrow();
        outboxPublisher.publishDueEvents();
        return outboxEventRepository.findById(paymentEvent.getId()).get();
    }

    private DeadLetterEvent newestDeadLetterEvent() {
        List<DeadLetterEvent> events = deadLetterEventRepository.findAll();
        return events.get(events.size() - 1);
    }

    private long createOrderAndMeasureLatency(String customerId, String sku) {
        CreateOrderRequest request = createOrderRequest(customerId, sku);
        Instant startedAt = Instant.now();
        ResponseEntity<OrderResponse> response = restTemplate.postForEntity("/api/orders", request, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Duration.between(startedAt, Instant.now()).toMillis();
    }

    private CreateOrderRequest createOrderRequest(String customerId, String sku) {
        return new CreateOrderRequest(customerId, List.of(new CreateOrderItemRequest(sku, 1)));
    }

    private ArrayNode scenarioIds(String... ids) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        for (String id : ids) {
            arrayNode.add(id);
        }
        return arrayNode;
    }

    private long percentile(List<Long> values, int percentile) {
        if (values.isEmpty()) {
            return 0;
        }

        List<Long> sortedValues = new ArrayList<>(values);
        Collections.sort(sortedValues);
        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }

    private void clearState() {
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

    private static String eventModeFor(String mode) {
        if ("baseline".equalsIgnoreCase(mode) || "direct".equalsIgnoreCase(mode)) {
            return "direct";
        }

        return "outbox-kafka";
    }

    private static int integerProperty(String propertyName, int defaultValue) {
        return Integer.parseInt(System.getProperty(propertyName, String.valueOf(defaultValue)));
    }

    private void assertAsyncReliabilityInvariants(String mode, ObjectNode benchmarkResult) {
        ObjectNode workflow = (ObjectNode) benchmarkResult.get("workflow");
        ObjectNode failureRecovery = (ObjectNode) benchmarkResult.get("failureRecovery");
        long attemptedOrders = workflow.get("attemptedOrders").asLong();

        assertThat(workflow.get("completedOrders").asLong()).isEqualTo(attemptedOrders);

        if ("direct".equalsIgnoreCase(eventModeFor(mode))) {
            assertThat(workflow.get("outboxEvents").asLong()).isZero();
            assertThat(workflow.get("dlqCount").asLong()).isZero();
            assertThat(failureRecovery.get("retryCount").asInt()).isZero();
            return;
        }

        assertThat(workflow.get("publishedEvents").asLong()).isEqualTo(attemptedOrders * 2);
        assertThat(workflow.get("processedEvents").asLong()).isEqualTo(attemptedOrders * 2);
        assertThat(workflow.get("pendingOutboxEvents").asLong()).isZero();
        assertThat(((ObjectNode) failureRecovery.get("consumerCrash")).get("statusAfterRetry").asText())
                .isEqualTo("PROCESSED");
        assertThat(((ObjectNode) failureRecovery.get("manualRetrySuccess")).get("retryStatus").asInt())
                .isEqualTo(202);
        assertThat(((ObjectNode) failureRecovery.get("manualRetrySuccess")).get("orderStatus").asText())
                .isEqualTo("COMPLETED");
        assertThat(((ObjectNode) failureRecovery.get("manualRetryFailure")).get("retryStatus").asInt())
                .isEqualTo(409);
        assertThat(failureRecovery.get("retryCount").asInt()).isGreaterThan(0);
        assertThat(failureRecovery.get("dlqCount").asInt()).isGreaterThan(0);
    }
}
