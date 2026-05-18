package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.audit.OrderAuditLogRepository;
import com.orderflow.idempotency.IdempotencyRecordRepository;
import com.orderflow.inventory.InventoryItemRepository;
import com.orderflow.inventory.SeedInventoryRequest;
import com.orderflow.payment.PaymentAttemptRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class InventoryConcurrencyIntegrationTest {

    private static final int CONCURRENT_ATTEMPTS = 200;
    private static final int INITIAL_STOCK = 25;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
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
    void concurrentCheckoutAttemptsDoNotOversellInventory() throws Exception {
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest("SKU-CONCURRENT", INITIAL_STOCK), Void.class);
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<HttpStatus>> futures = new ArrayList<>();

        for (int index = 0; index < CONCURRENT_ATTEMPTS; index++) {
            futures.add(executorService.submit(createCheckoutAttempt(startSignal, index)));
        }

        startSignal.countDown();
        List<HttpStatus> statuses = new ArrayList<>();
        for (Future<HttpStatus> future : futures) {
            statuses.add(future.get());
        }
        executorService.shutdown();

        long successfulOrders = statuses.stream()
                .filter(status -> status == HttpStatus.CREATED)
                .count();
        long rejectedOrders = statuses.stream()
                .filter(status -> status == HttpStatus.CONFLICT)
                .count();

        assertThat(successfulOrders).isLessThanOrEqualTo(INITIAL_STOCK);
        assertThat(successfulOrders + rejectedOrders).isEqualTo(CONCURRENT_ATTEMPTS);
        assertThat(orderRepository.count()).isEqualTo(successfulOrders);
        assertThat(paymentAttemptRepository.count()).isEqualTo(successfulOrders);
        assertThat(inventoryItemRepository.findBySku("SKU-CONCURRENT").get().getAvailableQuantity()).isGreaterThanOrEqualTo(0);
    }

    private Callable<HttpStatus> createCheckoutAttempt(CountDownLatch startSignal, int index) {
        return () -> {
            startSignal.await();
            CreateOrderRequest request = new CreateOrderRequest(
                    "customer-concurrent-" + index,
                    List.of(new CreateOrderItemRequest("SKU-CONCURRENT", 1))
            );
            ResponseEntity<OrderResponse> response = restTemplate.postForEntity("/api/orders", request, OrderResponse.class);
            return HttpStatus.valueOf(response.getStatusCode().value());
        };
    }
}
