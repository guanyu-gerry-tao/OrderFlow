package com.orderflow.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.orderflow.api.ApiErrorResponse;
import com.orderflow.audit.OrderAuditLogRepository;
import com.orderflow.dlq.DeadLetterEventRepository;
import com.orderflow.events.OrderEventType;
import com.orderflow.failure.FailureInjectionService;
import com.orderflow.idempotency.IdempotencyRecordRepository;
import com.orderflow.inventory.InventoryItemRepository;
import com.orderflow.order.CreateOrderItemRequest;
import com.orderflow.order.OrderRepository;
import com.orderflow.order.OrderStatus;
import com.orderflow.outbox.OutboxEventRepository;
import com.orderflow.payment.PaymentAttempt;
import com.orderflow.payment.PaymentAttemptRepository;
import com.orderflow.payment.PaymentRequestAttempt;
import com.orderflow.payment.PaymentRequestAttemptRepository;
import com.orderflow.payment.PaymentRequestAttemptStatus;
import com.orderflow.payment.PaymentService;
import com.orderflow.payment.PaymentStatus;
import java.time.Duration;
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
class CheckoutSessionIntegrationTest {

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
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CheckoutSessionRepository checkoutSessionRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private PaymentRequestAttemptRepository paymentRequestAttemptRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OrderAuditLogRepository orderAuditLogRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private DeadLetterEventRepository deadLetterEventRepository;

    @Autowired
    private FailureInjectionService failureInjectionService;

    @Autowired
    private PaymentService paymentService;

    @BeforeEach
    void clearDatabase() {
        failureInjectionService.clear();
        deadLetterEventRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        paymentRequestAttemptRepository.deleteAll();
        checkoutSessionRepository.deleteAll();
        paymentAttemptRepository.deleteAll();
        outboxEventRepository.deleteAll();
        orderAuditLogRepository.deleteAll();
        orderRepository.deleteAll();
        inventoryItemRepository.deleteAll();
    }

    @Test
    void createsCheckoutSessionWithPendingOrderAndFifteenMinuteExpiry() {
        CheckoutSessionResponse response = createCheckoutSession("customer-checkout", "SKU-CHECKOUT");

        assertThat(response.status()).isEqualTo(CheckoutSessionStatus.ACTIVE);
        assertThat(response.order().status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(Duration.between(response.createdAt(), response.expiresAt()).toMinutes()).isEqualTo(15);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(paymentAttemptRepository.count()).isEqualTo(1);
        assertThat(paymentRequestAttemptRepository.count()).isEqualTo(0);
        assertThat(outboxEventRepository.count()).isEqualTo(0);
        assertThat(response.paymentAttempt().idempotencyKey()).isEqualTo("authorize:" + response.paymentAttemptId());
    }

    @Test
    void repeatedConfirmWithSamePaymentIdempotencyKeyAuthorizesOnce() {
        CheckoutSessionResponse session = createCheckoutSession("customer-repeat", "SKU-REPEAT");
        ConfirmCheckoutRequest confirm = new ConfirmCheckoutRequest("mock-token-repeat");

        ResponseEntity<CheckoutSessionResponse> first = restTemplate.postForEntity(
                "/api/checkout-sessions/" + session.checkoutSessionId() + "/confirm",
                confirm,
                CheckoutSessionResponse.class
        );
        ResponseEntity<CheckoutSessionResponse> second = restTemplate.postForEntity(
                "/api/checkout-sessions/" + session.checkoutSessionId() + "/confirm",
                confirm,
                CheckoutSessionResponse.class
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isNotNull();
        assertThat(first.getBody()).isNotNull();
        assertThat(second.getBody().order().orderId()).isEqualTo(first.getBody().order().orderId());
        assertThat(session.paymentAttemptId()).isEqualTo(first.getBody().paymentAttempt().paymentAttemptId());
        assertThat(first.getBody().paymentAttempt().idempotencyKey()).isEqualTo("authorize:" + session.paymentAttemptId());
        assertThat(paymentAttemptRepository.findByOrderId(first.getBody().order().orderId())).hasSize(1);
        assertThat(paymentRequestAttemptRepository.findByPaymentAttemptIdOrderByCreatedAtAsc(session.paymentAttemptId()))
                .extracting(PaymentRequestAttempt::getStatus)
                .containsExactly(PaymentRequestAttemptStatus.AUTHORIZED, PaymentRequestAttemptStatus.REPLAYED);
        assertThat(outboxEventRepository.findAll()).hasSize(1);
        assertThat(outboxEventRepository.findAll().get(0).getEventType()).isEqualTo(OrderEventType.ORDER_CREATED);
    }

    @Test
    void gatewayTimeoutRetryUsesSamePaymentAttemptAndNewRequestAttempt() {
        CheckoutSessionResponse session = createCheckoutSession("customer-timeout", "SKU-TIMEOUT");
        failureInjectionService.failNextPaymentGatewayResponse(session.paymentAttemptId());
        ConfirmCheckoutRequest confirm = new ConfirmCheckoutRequest("mock-token-timeout");

        ResponseEntity<ApiErrorResponse> timeout = restTemplate.postForEntity(
                "/api/checkout-sessions/" + session.checkoutSessionId() + "/confirm",
                confirm,
                ApiErrorResponse.class
        );
        ResponseEntity<CheckoutSessionResponse> retry = restTemplate.postForEntity(
                "/api/checkout-sessions/" + session.checkoutSessionId() + "/confirm",
                confirm,
                CheckoutSessionResponse.class
        );

        assertThat(timeout.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getBody()).isNotNull();
        assertThat(retry.getBody().paymentAttempt().paymentAttemptId()).isEqualTo(session.paymentAttemptId());
        assertThat(retry.getBody().paymentAttempt().idempotencyKey()).isEqualTo("authorize:" + session.paymentAttemptId());
        assertThat(paymentAttemptRepository.findByOrderId(retry.getBody().order().orderId())).hasSize(1);
        assertThat(paymentRequestAttemptRepository.findByPaymentAttemptIdOrderByCreatedAtAsc(session.paymentAttemptId()))
                .extracting(PaymentRequestAttempt::getStatus)
                .containsExactly(PaymentRequestAttemptStatus.TIMEOUT, PaymentRequestAttemptStatus.AUTHORIZED);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void expiredCheckoutCannotBeConfirmed() {
        CheckoutSessionResponse session = createCheckoutSession("customer-expired", "SKU-EXPIRED");
        CheckoutSession checkoutSession = checkoutSessionRepository.findById(session.checkoutSessionId()).get();
        checkoutSession.expire();
        checkoutSessionRepository.save(checkoutSession);

        ResponseEntity<ApiErrorResponse> response = restTemplate.postForEntity(
                "/api/checkout-sessions/" + session.checkoutSessionId() + "/confirm",
                new ConfirmCheckoutRequest("mock-token-expired"),
                ApiErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("expired");
        assertThat(orderRepository.findById(session.order().orderId()).get().getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(paymentAttemptRepository.findById(session.paymentAttemptId()).get().getStatus())
                .isEqualTo(PaymentStatus.EXPIRED);
        assertThat(paymentRequestAttemptRepository.findByPaymentAttemptIdOrderByCreatedAtAsc(session.paymentAttemptId()))
                .hasSize(1);
        assertThat(outboxEventRepository.count()).isEqualTo(0);
    }

    @Test
    void workerPaymentStepDoesNotCreateSecondAttemptAfterCheckoutAuthorization() {
        CheckoutSessionResponse session = createCheckoutSession("customer-worker", "SKU-WORKER");
        ResponseEntity<CheckoutSessionResponse> confirm = restTemplate.postForEntity(
                "/api/checkout-sessions/" + session.checkoutSessionId() + "/confirm",
                new ConfirmCheckoutRequest("mock-token-worker"),
                CheckoutSessionResponse.class
        );

        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);
        failureInjectionService.failPaymentAttempts(session.order().orderId(), 1);

        assertThatCode(() -> paymentService.authorize(orderRepository.findById(session.order().orderId()).get()))
                .doesNotThrowAnyException();
        assertThat(paymentAttemptRepository.findByOrderId(session.order().orderId())).hasSize(1);
    }

    private CheckoutSessionResponse createCheckoutSession(String customerId, String sku) {
        CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest(
                customerId,
                List.of(new CreateOrderItemRequest(sku, 1))
        );
        ResponseEntity<CheckoutSessionResponse> response = restTemplate.postForEntity(
                "/api/checkout-sessions",
                request,
                CheckoutSessionResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}
