package com.orderflow.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orderflow.audit.OrderAuditLogRepository;
import com.orderflow.idempotency.IdempotencyRecordRepository;
import com.orderflow.inventory.InventoryItemRepository;
import com.orderflow.inventory.SeedInventoryRequest;
import com.orderflow.order.CreateOrderItemRequest;
import com.orderflow.order.CreateOrderRequest;
import com.orderflow.order.OrderRepository;
import com.orderflow.order.OrderResponse;
import com.orderflow.payment.PaymentAttemptRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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

@Tag("benchmark")
@Testcontainers
@EnabledIfSystemProperty(named = "orderflow.benchmark.enabled", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderCorrectnessBenchmarkTest {

    private static final int INITIAL_STOCK = 25;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String mode = System.getProperty("orderflow.benchmark.mode", "improved");
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("orderflow.idempotency.mode", () -> idempotencyModeFor(mode));
        registry.add("orderflow.idempotency.cache", () -> cacheModeFor(mode));
        registry.add("orderflow.inventory.strategy", () -> inventoryStrategyFor(mode));
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
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearDatabase() {
        idempotencyRecordRepository.deleteAll();
        orderAuditLogRepository.deleteAll();
        paymentAttemptRepository.deleteAll();
        orderRepository.deleteAll();
        inventoryItemRepository.deleteAll();
    }

    @Test
    void writesOrderCorrectnessBenchmarkResult() throws Exception {
        String mode = System.getProperty("orderflow.benchmark.mode", "improved");
        ObjectNode benchmarkResult = objectMapper.createObjectNode();
        benchmarkResult.put("mode", mode);
        benchmarkResult.set("repeatedSubmit", runRepeatedSubmitScenario());
        clearDatabase();
        benchmarkResult.set("concurrentCheckout", runConcurrentCheckoutScenario());
        assertCorrectnessInvariants(mode, benchmarkResult);

        Path outputDirectory = Path.of(System.getProperty(
                "orderflow.benchmark.outputDir",
                "benchmarks/results/full/order-correctness"
        ));
        BenchmarkReportWriter writer = new BenchmarkReportWriter(objectMapper);
        BenchmarkReportFiles reportFiles = writer.writeReport(
                outputDirectory,
                mode,
                mode,
                "Order Correctness Benchmark",
                benchmarkResult
        );

        assertThat(Files.exists(reportFiles.jsonPath())).isTrue();
        assertThat(Files.exists(reportFiles.markdownPath())).isTrue();
    }

    private ObjectNode runRepeatedSubmitScenario() {
        int repeatedSubmitAttempts = integerProperty("orderflow.benchmark.repeatedSubmitAttempts", 20);
        restTemplate.postForEntity(
                "/api/inventory/seed",
                new SeedInventoryRequest("SKU-BENCH-IDEM", repeatedSubmitAttempts + 10),
                Void.class
        );
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                "customer-benchmark-idempotency",
                List.of(new CreateOrderItemRequest("SKU-BENCH-IDEM", 1))
        );
        HttpEntity<CreateOrderRequest> requestEntity = new HttpEntity<>(
                createOrderRequest,
                idempotencyHeaders("benchmark-idempotency-key")
        );
        Instant startedAt = Instant.now();
        List<ResponseEntity<OrderResponse>> responses = new ArrayList<>();

        for (int attempt = 0; attempt < repeatedSubmitAttempts; attempt++) {
            responses.add(restTemplate.postForEntity("/api/orders", requestEntity, OrderResponse.class));
        }

        long createdResponses = responses.stream()
                .filter(response -> response.getStatusCode() == HttpStatus.CREATED)
                .count();
        long logicalOrders = orderRepository.count();
        long duplicateOrders = Math.max(0, logicalOrders - 1);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("attempts", repeatedSubmitAttempts);
        result.put("createdResponses", createdResponses);
        result.put("logicalOrders", logicalOrders);
        result.put("duplicateOrders", duplicateOrders);
        result.put("durationMs", Duration.between(startedAt, Instant.now()).toMillis());
        return result;
    }

    private ObjectNode runConcurrentCheckoutScenario() throws Exception {
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest("SKU-BENCH-CONCURRENT", INITIAL_STOCK), Void.class);
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<HttpStatus>> futures = new ArrayList<>();
        Instant startedAt = Instant.now();
        int concurrentAttempts = integerProperty("orderflow.benchmark.concurrentAttempts", 200);

        for (int index = 0; index < concurrentAttempts; index++) {
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
        long failedReservations = statuses.stream()
                .filter(status -> status == HttpStatus.CONFLICT)
                .count();
        int finalInventory = inventoryItemRepository.findBySku("SKU-BENCH-CONCURRENT")
                .map(inventoryItem -> inventoryItem.getAvailableQuantity())
                .orElse(-1);
        long oversellCount = Math.max(0, successfulOrders - INITIAL_STOCK);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("attempts", concurrentAttempts);
        result.put("initialStock", INITIAL_STOCK);
        result.put("successfulOrders", successfulOrders);
        result.put("failedReservations", failedReservations);
        result.put("oversellCount", oversellCount);
        result.put("finalInventory", finalInventory);
        result.put("durationMs", Duration.between(startedAt, Instant.now()).toMillis());
        return result;
    }

    private Callable<HttpStatus> createCheckoutAttempt(CountDownLatch startSignal, int index) {
        return () -> {
            startSignal.await();
            CreateOrderRequest request = new CreateOrderRequest(
                    "customer-benchmark-concurrent-" + index,
                    List.of(new CreateOrderItemRequest("SKU-BENCH-CONCURRENT", 1))
            );
            ResponseEntity<OrderResponse> response = restTemplate.postForEntity("/api/orders", request, OrderResponse.class);
            return HttpStatus.valueOf(response.getStatusCode().value());
        };
    }

    private HttpHeaders idempotencyHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Idempotency-Key", idempotencyKey);
        return headers;
    }

    private static String idempotencyModeFor(String mode) {
        if ("baseline".equalsIgnoreCase(mode)) {
            return "baseline";
        }

        return "strict";
    }

    private static String cacheModeFor(String mode) {
        if ("baseline".equalsIgnoreCase(mode)) {
            return "disabled";
        }

        return "redis";
    }

    private static String inventoryStrategyFor(String mode) {
        if ("baseline".equalsIgnoreCase(mode)) {
            return "naive";
        }

        return "optimistic-locking";
    }

    private static int integerProperty(String propertyName, int defaultValue) {
        return Integer.parseInt(System.getProperty(propertyName, String.valueOf(defaultValue)));
    }

    private void assertCorrectnessInvariants(String mode, ObjectNode benchmarkResult) {
        ObjectNode repeatedSubmit = (ObjectNode) benchmarkResult.get("repeatedSubmit");
        ObjectNode concurrentCheckout = (ObjectNode) benchmarkResult.get("concurrentCheckout");

        if ("baseline".equalsIgnoreCase(mode)) {
            assertThat(repeatedSubmit.get("duplicateOrders").asLong()).isGreaterThan(0);
            return;
        }

        assertThat(repeatedSubmit.get("duplicateOrders").asLong()).isZero();
        assertThat(repeatedSubmit.get("logicalOrders").asLong()).isEqualTo(1);
        assertThat(concurrentCheckout.get("oversellCount").asLong()).isZero();
        assertThat(concurrentCheckout.get("successfulOrders").asLong())
                .isLessThanOrEqualTo(concurrentCheckout.get("initialStock").asLong());
    }
}
