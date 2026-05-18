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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderWorkflowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
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
    void createsAnOrderThroughTheFullSynchronousHappyPath() {
        SeedInventoryRequest seedInventoryRequest = new SeedInventoryRequest("SKU-M1", 10);
        ResponseEntity<Void> seedResponse = restTemplate.postForEntity("/api/inventory/seed", seedInventoryRequest, Void.class);
        assertThat(seedResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                "customer-101",
                List.of(new CreateOrderItemRequest("SKU-M1", 2))
        );
        ResponseEntity<OrderResponse> createResponse = restTemplate.postForEntity(
                "/api/orders",
                createOrderRequest,
                OrderResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().status()).isEqualTo(OrderStatus.COMPLETED);

        OrderResponse orderResponse = restTemplate.getForObject(
                "/api/orders/{orderId}",
                OrderResponse.class,
                createResponse.getBody().orderId()
        );
        assertThat(orderResponse.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(orderResponse.items()).hasSize(1);
        assertThat(orderResponse.items().get(0).sku()).isEqualTo("SKU-M1");

        TimelineResponse timelineResponse = restTemplate.getForObject(
                "/api/orders/{orderId}/timeline",
                TimelineResponse.class,
                createResponse.getBody().orderId()
        );
        assertThat(timelineResponse.events())
                .extracting(TimelineEventResponse::toStatus)
                .containsExactly(
                        OrderStatus.CREATED,
                        OrderStatus.INVENTORY_RESERVED,
                        OrderStatus.PAYMENT_AUTHORIZED,
                        OrderStatus.COMPLETED
                );

        assertThat(inventoryItemRepository.findBySku("SKU-M1")).isPresent();
        assertThat(inventoryItemRepository.findBySku("SKU-M1").get().getAvailableQuantity()).isEqualTo(8);
        assertThat(paymentAttemptRepository.findByOrderId(createResponse.getBody().orderId())).hasSize(1);
        assertThat(timelineResponse.events())
                .extracting(TimelineEventResponse::sequenceNumber)
                .containsExactly(1, 2, 3, 4);
        assertThat(orderAuditLogRepository.findByOrderIdOrderBySequenceNumberAsc(createResponse.getBody().orderId()))
                .hasSize(4);
    }

    @Test
    void completesOrderWhenRequestedQuantityExactlyMatchesAvailableInventory() {
        SeedInventoryRequest seedInventoryRequest = new SeedInventoryRequest("SKU-EXACT-STOCK", 2);
        restTemplate.postForEntity("/api/inventory/seed", seedInventoryRequest, Void.class);
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                "customer-exact-stock",
                List.of(new CreateOrderItemRequest("SKU-EXACT-STOCK", 2))
        );

        ResponseEntity<OrderResponse> createResponse = restTemplate.postForEntity(
                "/api/orders",
                createOrderRequest,
                OrderResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(inventoryItemRepository.findBySku("SKU-EXACT-STOCK").get().getAvailableQuantity()).isEqualTo(0);
    }

    @Test
    void rejectsOrderWhenInventorySkuIsMissing() {
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                "customer-missing-inventory",
                List.of(new CreateOrderItemRequest("SKU-DOES-NOT-EXIST", 1))
        );

        ResponseEntity<ApiErrorResponse> createResponse = restTemplate.postForEntity(
                "/api/orders",
                createOrderRequest,
                ApiErrorResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().code()).isEqualTo("conflict");
        assertThat(createResponse.getBody().message()).isNotBlank();
        assertThat(orderRepository.count()).isEqualTo(0);
        assertThat(paymentAttemptRepository.count()).isEqualTo(0);
        assertThat(orderAuditLogRepository.count()).isEqualTo(0);
    }

    @Test
    void rejectsOrderWhenInventoryQuantityIsInsufficient() {
        SeedInventoryRequest seedInventoryRequest = new SeedInventoryRequest("SKU-LOW-STOCK", 1);
        restTemplate.postForEntity("/api/inventory/seed", seedInventoryRequest, Void.class);
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                "customer-low-stock",
                List.of(new CreateOrderItemRequest("SKU-LOW-STOCK", 2))
        );

        ResponseEntity<ApiErrorResponse> createResponse = restTemplate.postForEntity(
                "/api/orders",
                createOrderRequest,
                ApiErrorResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().code()).isEqualTo("conflict");
        assertThat(createResponse.getBody().message()).isNotBlank();
        assertThat(orderRepository.count()).isEqualTo(0);
        assertThat(paymentAttemptRepository.count()).isEqualTo(0);
        assertThat(orderAuditLogRepository.count()).isEqualTo(0);
        assertThat(inventoryItemRepository.findBySku("SKU-LOW-STOCK").get().getAvailableQuantity()).isEqualTo(1);
    }

    @Test
    void rollsBackEarlierInventoryReservationWhenLaterLineItemFails() {
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest("SKU-ROLLBACK-A", 5), Void.class);
        restTemplate.postForEntity("/api/inventory/seed", new SeedInventoryRequest("SKU-ROLLBACK-B", 1), Void.class);
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                "customer-rollback",
                List.of(
                        new CreateOrderItemRequest("SKU-ROLLBACK-A", 2),
                        new CreateOrderItemRequest("SKU-ROLLBACK-B", 2)
                )
        );

        ResponseEntity<ApiErrorResponse> createResponse = restTemplate.postForEntity(
                "/api/orders",
                createOrderRequest,
                ApiErrorResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().code()).isEqualTo("conflict");
        assertThat(orderRepository.count()).isEqualTo(0);
        assertThat(paymentAttemptRepository.count()).isEqualTo(0);
        assertThat(orderAuditLogRepository.count()).isEqualTo(0);
        assertThat(inventoryItemRepository.findBySku("SKU-ROLLBACK-A").get().getAvailableQuantity()).isEqualTo(5);
        assertThat(inventoryItemRepository.findBySku("SKU-ROLLBACK-B").get().getAvailableQuantity()).isEqualTo(1);
    }

    @Test
    void rejectsOrderWhenRequestBodyIsInvalid() {
        CreateOrderRequest createOrderRequest = new CreateOrderRequest("", List.of());

        ResponseEntity<ApiErrorResponse> createResponse = restTemplate.postForEntity(
                "/api/orders",
                createOrderRequest,
                ApiErrorResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().code()).isEqualTo("invalid_request");
        assertThat(orderRepository.count()).isEqualTo(0);
        assertThat(paymentAttemptRepository.count()).isEqualTo(0);
    }

    @Test
    void rejectsOrderWhenLineItemIsNull() {
        String requestBody = """
                {
                  "customerId": "customer-null-item",
                  "items": [null]
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<ApiErrorResponse> createResponse = restTemplate.postForEntity(
                "/api/orders",
                requestEntity,
                ApiErrorResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().code()).isEqualTo("invalid_request");
        assertThat(orderRepository.count()).isEqualTo(0);
        assertThat(paymentAttemptRepository.count()).isEqualTo(0);
    }

    @Test
    void returnsNotFoundForMissingTimeline() {
        ResponseEntity<ApiErrorResponse> timelineResponse = restTemplate.getForEntity(
                "/api/orders/00000000-0000-0000-0000-000000000001/timeline",
                ApiErrorResponse.class
        );

        assertThat(timelineResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(timelineResponse.getBody()).isNotNull();
        assertThat(timelineResponse.getBody().code()).isEqualTo("404_not_found");
        assertThat(timelineResponse.getBody().message()).isNotBlank();
    }
}
