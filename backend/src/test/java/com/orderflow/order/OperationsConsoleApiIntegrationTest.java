package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.audit.OrderAuditLogRepository;
import com.orderflow.dlq.DeadLetterEvent;
import com.orderflow.dlq.DeadLetterEventRepository;
import com.orderflow.events.OrderEventType;
import com.orderflow.idempotency.IdempotencyRecordRepository;
import com.orderflow.inventory.InventoryItemRepository;
import com.orderflow.inventory.InventoryItemResponse;
import com.orderflow.inventory.SeedInventoryRequest;
import com.orderflow.operations.OperationsHealthResponse;
import com.orderflow.outbox.OutboxEvent;
import com.orderflow.outbox.OutboxEventRepository;
import com.orderflow.outbox.OutboxEventStatus;
import com.orderflow.payment.PaymentAttemptRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
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
class OperationsConsoleApiIntegrationTest {

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
        registry.add("orderflow.events.mode", () -> "direct");
        registry.add("orderflow.events.broker", () -> "recording");
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

    @BeforeEach
    void clearDatabase() {
        deadLetterEventRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        orderAuditLogRepository.deleteAll();
        paymentAttemptRepository.deleteAll();
        outboxEventRepository.deleteAll();
        orderRepository.deleteAll();
        inventoryItemRepository.deleteAll();
    }

    @Test
    void listsOrdersForStatusAndTextSearch() {
        createCompletedOrder("SKU-CONSOLE-A", "customer-console-a");
        createCompletedOrder("SKU-CONSOLE-B", "customer-console-b");

        ResponseEntity<List<OrderResponse>> statusResponse = restTemplate.exchange(
                "/api/orders?status=COMPLETED",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                }
        );
        ResponseEntity<List<OrderResponse>> searchResponse = restTemplate.exchange(
                "/api/orders?search=SKU-CONSOLE-B",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                }
        );

        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusResponse.getBody()).hasSize(2);
        assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(searchResponse.getBody()).hasSize(1);
        assertThat(searchResponse.getBody().get(0).customerId()).isEqualTo("customer-console-b");
    }

    @Test
    void listsInventoryForDashboardDisplay() {
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest("SKU-DASHBOARD-A", 7), Void.class);
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest("SKU-DASHBOARD-B", 3), Void.class);

        ResponseEntity<List<InventoryItemResponse>> response = restTemplate.exchange(
                "/api/inventory",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                }
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(InventoryItemResponse::sku)
                .containsExactly("SKU-DASHBOARD-A", "SKU-DASHBOARD-B");
        assertThat(response.getBody()).extracting(InventoryItemResponse::availableQuantity)
                .containsExactly(7, 3);
    }

    @Test
    void returnsHealthCountersForTroubleshootingView() {
        createCompletedOrder("SKU-HEALTH", "customer-health");
        OutboxEvent failedEvent = new OutboxEvent(UUID.randomUUID(), OrderEventType.ORDER_CREATED, "{}");
        failedEvent.recordRetry("Injected console health failure", java.time.Duration.ZERO);
        failedEvent.markDeadLettered();
        outboxEventRepository.saveAndFlush(failedEvent);
        deadLetterEventRepository.save(new DeadLetterEvent(failedEvent));

        OperationsHealthResponse response = restTemplate.getForObject(
                "/api/operations/health",
                OperationsHealthResponse.class
        );

        assertThat(response.backendStatus()).isEqualTo("UP");
        assertThat(response.databaseStatus()).isEqualTo("UP");
        assertThat(response.orderCount()).isEqualTo(1);
        assertThat(response.inventorySkuCount()).isEqualTo(1);
        assertThat(response.dlqCount()).isEqualTo(1);
        assertThat(response.openDlqCount()).isEqualTo(1);
        assertThat(response.retryCount()).isEqualTo(1);
        assertThat(response.outboxCounts().get(OutboxEventStatus.DLQ.name())).isEqualTo(1);
    }

    private void createCompletedOrder(String sku, String customerId) {
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest(sku, 10), Void.class);
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                customerId,
                List.of(new CreateOrderItemRequest(sku, 1))
        );

        ResponseEntity<OrderResponse> response = restTemplate.postForEntity(
                "/api/orders",
                createOrderRequest,
                OrderResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.COMPLETED);
    }
}
