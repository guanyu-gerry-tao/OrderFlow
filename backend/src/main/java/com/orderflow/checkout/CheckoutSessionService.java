package com.orderflow.checkout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.audit.OrderAuditLogRepository;
import com.orderflow.audit.OrderAuditService;
import com.orderflow.events.OrderEventType;
import com.orderflow.order.CreateOrderItemRequest;
import com.orderflow.order.OrderEntity;
import com.orderflow.order.OrderItemResponse;
import com.orderflow.order.OrderRepository;
import com.orderflow.order.OrderResponse;
import com.orderflow.order.OrderStateMachine;
import com.orderflow.order.OrderStatus;
import com.orderflow.outbox.OutboxService;
import com.orderflow.payment.PaymentAttempt;
import com.orderflow.payment.PaymentAttemptResponse;
import com.orderflow.payment.PaymentRequestAttempt;
import com.orderflow.payment.PaymentRequestAttemptStatus;
import com.orderflow.payment.PaymentService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Coordinates checkout sessions, pending orders, and payment confirmation.
 */
@Service
public class CheckoutSessionService {

    private static final Duration CHECKOUT_TTL = Duration.ofMinutes(15);

    private final CheckoutSessionRepository checkoutSessionRepository;
    private final OrderRepository orderRepository;
    private final OrderAuditService orderAuditService;
    private final OrderAuditLogRepository orderAuditLogRepository;
    private final PaymentService paymentService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final OrderStateMachine orderStateMachine = new OrderStateMachine();

    /**
     * Creates a checkout session service.
     *
     * @param checkoutSessionRepository checkout session repository
     * @param orderRepository order repository
     * @param orderAuditService audit service
     * @param orderAuditLogRepository audit repository
     * @param paymentService payment service
     * @param outboxService outbox service
     * @param objectMapper JSON mapper
     */
    public CheckoutSessionService(
            CheckoutSessionRepository checkoutSessionRepository,
            OrderRepository orderRepository,
            OrderAuditService orderAuditService,
            OrderAuditLogRepository orderAuditLogRepository,
            PaymentService paymentService,
            OutboxService outboxService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.orderRepository = orderRepository;
        this.orderAuditService = orderAuditService;
        this.orderAuditLogRepository = orderAuditLogRepository;
        this.paymentService = paymentService;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Starts checkout with a pending order and active payment attempt.
     *
     * @param request checkout request
     * @return checkout session response
     */
    @Transactional
    public CheckoutSessionResponse createSession(CreateCheckoutSessionRequest request) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(CHECKOUT_TTL);
        OrderEntity order = OrderEntity.pendingPayment(request.customerId(), expiresAt);
        for (CreateOrderItemRequest itemRequest : request.items()) {
            order.addItem(itemRequest.sku(), itemRequest.quantity());
        }
        orderRepository.saveAndFlush(order);
        orderAuditService.record(order.getId(), 1, null, OrderStatus.PENDING_PAYMENT, "Checkout started");

        PaymentAttempt paymentAttempt = paymentService.createInitiatedAttempt(order.getId(), expiresAt);
        CheckoutSession checkoutSession = checkoutSessionRepository.save(new CheckoutSession(
                order.getId(),
                paymentAttempt.getId(),
                expiresAt,
                now
        ));

        return toResponse(checkoutSession, order, paymentAttempt, null);
    }

    /**
     * Fetches one checkout session.
     *
     * @param checkoutSessionId checkout session id
     * @return checkout session response
     */
    @Transactional(readOnly = true)
    public CheckoutSessionResponse getSession(UUID checkoutSessionId) {
        CheckoutSession checkoutSession = loadSession(checkoutSessionId);
        OrderEntity order = loadOrder(checkoutSession.getOrderId());
        PaymentAttempt paymentAttempt = loadPaymentAttempt(checkoutSession.getActivePaymentAttemptId());
        return toResponse(checkoutSession, order, paymentAttempt, null);
    }

    /**
     * Confirms the active payment attempt for a checkout session.
     *
     * @param checkoutSessionId checkout session id
     * @param request confirm request
     * @return confirmed checkout session response
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public CheckoutSessionResponse confirm(UUID checkoutSessionId, ConfirmCheckoutRequest request) {
        CheckoutSession checkoutSession = loadSession(checkoutSessionId);
        OrderEntity order = loadOrder(checkoutSession.getOrderId());
        PaymentAttempt paymentAttempt = loadPaymentAttempt(checkoutSession.getActivePaymentAttemptId());
        PaymentRequestAttempt requestAttempt = paymentService.startRequestAttempt(paymentAttempt);

        Instant now = clock.instant();
        if (checkoutSession.getStatus() == CheckoutSessionStatus.EXPIRED
                || now.isAfter(checkoutSession.getExpiresAt())) {
            expire(checkoutSession, order, paymentAttempt);
            requestAttempt.complete(PaymentRequestAttemptStatus.EXPIRED, "Checkout expired", now);
            paymentService.save(requestAttempt);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout session expired");
        }

        if (paymentService.shouldSimulateGatewayTimeout(paymentAttempt.getId())) {
            requestAttempt.complete(PaymentRequestAttemptStatus.TIMEOUT, "Simulated gateway response timeout", now);
            paymentService.save(requestAttempt);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Payment gateway response timed out");
        }

        if (paymentAttempt.getStatus() == com.orderflow.payment.PaymentStatus.AUTHORIZED) {
            requestAttempt.complete(PaymentRequestAttemptStatus.REPLAYED, "Payment confirmation replayed", now);
            paymentService.save(requestAttempt);
            return toResponse(checkoutSession, order, paymentAttempt, requestAttempt);
        }

        String requestHash = hashRequest(request);
        transition(order, OrderStatus.CREATED, "Payment confirmed");
        paymentAttempt.authorize(requestHash, "Payment authorized", now);
        checkoutSession.confirm(now);
        outboxService.enqueueOrderEvent(order.getId(), OrderEventType.ORDER_CREATED);
        requestAttempt.complete(PaymentRequestAttemptStatus.AUTHORIZED, "Payment authorized", now);

        paymentService.save(paymentAttempt);
        paymentService.save(requestAttempt);
        CheckoutSession savedSession = checkoutSessionRepository.save(checkoutSession);
        OrderEntity savedOrder = orderRepository.save(order);
        CheckoutSessionResponse response = toResponse(savedSession, savedOrder, paymentAttempt, requestAttempt);
        paymentAttempt.authorize(requestHash, serialize(response), now);
        paymentService.save(paymentAttempt);
        return response;
    }

    private void expire(CheckoutSession checkoutSession, OrderEntity order, PaymentAttempt paymentAttempt) {
        Instant now = clock.instant();
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            transition(order, OrderStatus.EXPIRED, "Checkout expired");
        }
        checkoutSession.expire(now);
        paymentAttempt.expire(now);
        checkoutSessionRepository.save(checkoutSession);
        orderRepository.save(order);
        paymentService.save(paymentAttempt);
    }

    private CheckoutSession loadSession(UUID checkoutSessionId) {
        return checkoutSessionRepository.findById(checkoutSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout session not found"));
    }

    private OrderEntity loadOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private PaymentAttempt loadPaymentAttempt(UUID paymentAttemptId) {
        return paymentService.findAttempt(paymentAttemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment attempt not found"));
    }

    private void transition(OrderEntity order, OrderStatus nextStatus, String message) {
        OrderStatus previousStatus = order.getStatus();
        orderStateMachine.validateTransition(previousStatus, nextStatus);
        order.updateStatus(nextStatus);
        orderAuditService.record(order.getId(), nextSequenceNumber(order.getId()), previousStatus, nextStatus, message);
    }

    private int nextSequenceNumber(UUID orderId) {
        return orderAuditLogRepository.findByOrderIdOrderBySequenceNumberAsc(orderId).size() + 1;
    }

    private CheckoutSessionResponse toResponse(
            CheckoutSession checkoutSession,
            OrderEntity order,
            PaymentAttempt paymentAttempt,
            PaymentRequestAttempt requestAttempt
    ) {
        return new CheckoutSessionResponse(
                checkoutSession.getId(),
                checkoutSession.getStatus(),
                toOrderResponse(order),
                new PaymentAttemptResponse(
                        paymentAttempt.getId(),
                        paymentAttempt.getIdempotencyKey(),
                        paymentAttempt.getStatus(),
                        paymentAttempt.getExpiresAt()
                ),
                requestAttempt == null ? null : requestAttempt.getId(),
                requestAttempt == null ? null : requestAttempt.getStatus(),
                checkoutSession.getCreatedAt(),
                checkoutSession.getExpiresAt()
        );
    }

    private OrderResponse toOrderResponse(OrderEntity order) {
        List<OrderItemResponse> items = order.getItems()
                .stream()
                .map(item -> new OrderItemResponse(item.getId(), item.getSku(), item.getQuantity()))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private String hashRequest(ConfirmCheckoutRequest request) {
        try {
            // TODO(out-of-scope): keep this as an opaque fingerprint until a real provider payload contract exists.
            String serializedRequest = objectMapper.writeValueAsString(request);
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = messageDigest.digest(serializedRequest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash payment confirmation request", exception);
        }
    }

    private String serialize(CheckoutSessionResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize checkout confirmation response", exception);
        }
    }
}
