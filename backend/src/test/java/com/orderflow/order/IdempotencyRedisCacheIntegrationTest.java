package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotencyRedisCacheIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("orderflow.events.mode", () -> "direct");
        registry.add("orderflow.events.broker", () -> "recording");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

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
        try (RedisConnection redisConnection = redisTemplate.getConnectionFactory().getConnection()) {
            redisConnection.serverCommands().flushDb();
        }
        idempotencyRecordRepository.deleteAll();
        orderAuditLogRepository.deleteAll();
        paymentAttemptRepository.deleteAll();
        orderRepository.deleteAll();
        inventoryItemRepository.deleteAll();
    }

    @Test
    void completedIdempotentResponseIsStoredInRedisCache() {
        String idempotencyKey = "idem-cache-001";
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest("SKU-CACHE", 5), Void.class);
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                "customer-cache",
                List.of(new CreateOrderItemRequest("SKU-CACHE", 1))
        );
        HttpEntity<CreateOrderRequest> requestEntity = new HttpEntity<>(
                createOrderRequest,
                idempotencyHeaders(idempotencyKey)
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
        assertThat(redisTemplate.hasKey("orderflow:idempotency:" + idempotencyKey)).isTrue();
        assertThat(idempotencyRecordRepository.count()).isEqualTo(1);
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    private HttpHeaders idempotencyHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Idempotency-Key", idempotencyKey);
        return headers;
    }
}
