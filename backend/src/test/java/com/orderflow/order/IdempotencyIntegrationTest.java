package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.api.ApiErrorResponse;
import com.orderflow.audit.OrderAuditLogRepository;
import com.orderflow.idempotency.IdempotencyRecordRepository;
import com.orderflow.inventory.InventoryItemRepository;
import com.orderflow.inventory.SeedInventoryRequest;
import com.orderflow.payment.PaymentAttemptRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
class IdempotencyIntegrationTest {

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

    @BeforeEach
    void clearDatabase() {
        idempotencyRecordRepository.deleteAll();
        orderAuditLogRepository.deleteAll();
        paymentAttemptRepository.deleteAll();
        orderRepository.deleteAll();
        inventoryItemRepository.deleteAll();
    }

    @Test
    void repeatedSubmitWithSameIdempotencyKeyReturnsOneLogicalOrder() {
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest("SKU-IDEMPOTENT", 10), Void.class);
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                "customer-idempotent",
                List.of(new CreateOrderItemRequest("SKU-IDEMPOTENT", 2))
        );
        HttpEntity<CreateOrderRequest> requestEntity = new HttpEntity<>(
                createOrderRequest,
                idempotencyHeaders("idem-repeat-001")
        );

        ResponseEntity<OrderResponse> firstResponse = restTemplate.postForEntity(
                "/api/orders",
                requestEntity,
                OrderResponse.class
        );
        ResponseEntity<OrderResponse> secondResponse = restTemplate.postForEntity(
                "/api/orders",
                requestEntity,
                OrderResponse.class
        );

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody().orderId()).isEqualTo(firstResponse.getBody().orderId());
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(paymentAttemptRepository.count()).isEqualTo(1);
        assertThat(orderAuditLogRepository.count()).isEqualTo(4);
        assertThat(inventoryItemRepository.findBySku("SKU-IDEMPOTENT").get().getAvailableQuantity()).isEqualTo(8);
    }

    @Test
    void sameIdempotencyKeyWithDifferentRequestBodyReturnsConflict() {
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest("SKU-CONFLICT", 10), Void.class);
        CreateOrderRequest firstRequest = new CreateOrderRequest(
                "customer-conflict",
                List.of(new CreateOrderItemRequest("SKU-CONFLICT", 1))
        );
        CreateOrderRequest conflictingRequest = new CreateOrderRequest(
                "customer-conflict",
                List.of(new CreateOrderItemRequest("SKU-CONFLICT", 3))
        );
        HttpHeaders headers = idempotencyHeaders("idem-conflict-001");

        ResponseEntity<OrderResponse> firstResponse = restTemplate.postForEntity(
                "/api/orders",
                new HttpEntity<>(firstRequest, headers),
                OrderResponse.class
        );
        ResponseEntity<ApiErrorResponse> conflictResponse = restTemplate.postForEntity(
                "/api/orders",
                new HttpEntity<>(conflictingRequest, headers),
                ApiErrorResponse.class
        );

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(conflictResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflictResponse.getBody()).isNotNull();
        assertThat(conflictResponse.getBody().code()).isEqualTo("conflict");
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(paymentAttemptRepository.count()).isEqualTo(1);
        assertThat(inventoryItemRepository.findBySku("SKU-CONFLICT").get().getAvailableQuantity()).isEqualTo(9);
    }

    @Test
    void repeatedSubmitFallsBackToPostgresWhenRedisIsUnavailable() {
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest("SKU-REDIS-DOWN", 5), Void.class);
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                "customer-redis-down",
                List.of(new CreateOrderItemRequest("SKU-REDIS-DOWN", 1))
        );
        HttpEntity<CreateOrderRequest> requestEntity = new HttpEntity<>(
                createOrderRequest,
                idempotencyHeaders("idem-redis-down-001")
        );

        ResponseEntity<OrderResponse> firstResponse = restTemplate.postForEntity(
                "/api/orders",
                requestEntity,
                OrderResponse.class
        );
        ResponseEntity<OrderResponse> secondResponse = restTemplate.postForEntity(
                "/api/orders",
                requestEntity,
                OrderResponse.class
        );

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody().orderId()).isEqualTo(firstResponse.getBody().orderId());
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(inventoryItemRepository.findBySku("SKU-REDIS-DOWN").get().getAvailableQuantity()).isEqualTo(4);
    }

    private HttpHeaders idempotencyHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Idempotency-Key", idempotencyKey);
        return headers;
    }
}
