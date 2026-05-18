package com.orderflow.order;

import com.orderflow.audit.OrderAuditLog;
import com.orderflow.audit.OrderAuditLogRepository;
import com.orderflow.audit.OrderAuditService;
import com.orderflow.idempotency.IdempotencyService;
import com.orderflow.inventory.InventoryService;
import com.orderflow.events.OrderEventType;
import com.orderflow.outbox.EventMode;
import com.orderflow.outbox.OutboxService;
import com.orderflow.payment.PaymentService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Coordinates the synchronous M1 order workflow.
 */
@Service
public class OrderWorkflowService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final OrderAuditService orderAuditService;
    private final OrderAuditLogRepository orderAuditLogRepository;
    private final OrderStateMachine orderStateMachine;
    private final IdempotencyService idempotencyService;
    private final EventMode eventMode;
    private final OutboxService outboxService;

    /**
     * Creates an order workflow service.
     *
     * @param orderRepository order repository
     * @param inventoryService inventory reservation service
     * @param paymentService payment simulation service
     * @param orderAuditService audit writer
     * @param orderAuditLogRepository audit query repository
     * @param idempotencyService idempotency coordinator
     * @param eventMode event processing mode
     * @param outboxService transactional outbox writer
     */
    public OrderWorkflowService(
            OrderRepository orderRepository,
            InventoryService inventoryService,
            PaymentService paymentService,
            OrderAuditService orderAuditService,
            OrderAuditLogRepository orderAuditLogRepository,
            IdempotencyService idempotencyService,
            EventMode eventMode,
            OutboxService outboxService
    ) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.orderAuditService = orderAuditService;
        this.orderAuditLogRepository = orderAuditLogRepository;
        this.idempotencyService = idempotencyService;
        this.eventMode = eventMode;
        this.outboxService = outboxService;
        this.orderStateMachine = new OrderStateMachine();
    }

    /**
     * Creates an order and completes the synchronous workflow.
     *
     * @param request order creation request
     * @param idempotencyKey optional idempotency key
     * @return completed order response
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        if (!idempotencyService.shouldApply(idempotencyKey)) {
            return createNewOrder(request);
        }

        String normalizedKey = idempotencyService.normalizeKey(idempotencyKey);
        String requestHash = idempotencyService.hashRequest(request);
        Optional<OrderResponse> cachedResponse = idempotencyService.findCachedResponse(normalizedKey, requestHash);
        if (cachedResponse.isPresent()) {
            return cachedResponse.get();
        }

        idempotencyService.lockKey(normalizedKey);
        Optional<OrderResponse> storedResponse = idempotencyService.findStoredResponse(normalizedKey, requestHash);
        if (storedResponse.isPresent()) {
            return storedResponse.get();
        }

        OrderResponse response = createNewOrder(request);
        idempotencyService.recordCompletedResponse(normalizedKey, requestHash, response);
        return response;
    }

    /**
     * Creates an order without idempotency replay.
     *
     * @param request order creation request
     * @return completed order response
     */
    public OrderResponse createOrder(CreateOrderRequest request) {
        return createOrder(request, null);
    }

    private OrderResponse createNewOrder(CreateOrderRequest request) {
        if (eventMode.isOutboxKafka()) {
            return createOutboxOrder(request);
        }

        return createSynchronousOrder(request);
    }

    private OrderResponse createOutboxOrder(CreateOrderRequest request) {
        OrderEntity order = new OrderEntity(request.customerId());

        // Persist the order and outbox event in the same transaction.
        for (CreateOrderItemRequest itemRequest : request.items()) {
            order.addItem(itemRequest.sku(), itemRequest.quantity());
        }
        orderRepository.saveAndFlush(order);
        orderAuditService.record(order.getId(), 1, null, OrderStatus.CREATED, "Order created");
        outboxService.enqueueOrderEvent(order.getId(), OrderEventType.ORDER_CREATED);

        return toOrderResponse(order);
    }

    private OrderResponse createSynchronousOrder(CreateOrderRequest request) {
        OrderEntity order = new OrderEntity(request.customerId());

        // Persist the order and its line items before reserving inventory.
        for (CreateOrderItemRequest itemRequest : request.items()) {
            order.addItem(itemRequest.sku(), itemRequest.quantity());
        }
        orderRepository.saveAndFlush(order);
        orderAuditService.record(order.getId(), 1, null, OrderStatus.CREATED, "Order created");

        // Reserve inventory synchronously for the M1 happy path.
        for (OrderItemEntity orderItem : order.getItems()) {
            inventoryService.reserve(orderItem.getSku(), orderItem.getQuantity());
        }
        transition(order, 2, OrderStatus.INVENTORY_RESERVED, "Inventory reserved");

        // Authorize the simulated payment and complete the workflow.
        paymentService.authorize(order);
        transition(order, 3, OrderStatus.PAYMENT_AUTHORIZED, "Payment authorized");
        transition(order, 4, OrderStatus.COMPLETED, "Order completed");

        return toOrderResponse(orderRepository.save(order));
    }

    /**
     * Fetches an order by id.
     *
     * @param orderId order identifier
     * @return order response
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        return toOrderResponse(order);
    }

    /**
     * Fetches an order audit timeline.
     *
     * @param orderId order identifier
     * @return timeline response
     */
    @Transactional(readOnly = true)
    public TimelineResponse getTimeline(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }

        List<TimelineEventResponse> events = orderAuditLogRepository.findByOrderIdOrderBySequenceNumberAsc(orderId)
                .stream()
                .map(this::toTimelineEventResponse)
                .toList();

        return new TimelineResponse(events);
    }

    private void transition(OrderEntity order, int sequenceNumber, OrderStatus nextStatus, String message) {
        OrderStatus previousStatus = order.getStatus();
        orderStateMachine.validateTransition(previousStatus, nextStatus);
        order.updateStatus(nextStatus);
        orderAuditService.record(order.getId(), sequenceNumber, previousStatus, nextStatus, message);
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

    private TimelineEventResponse toTimelineEventResponse(OrderAuditLog auditLog) {
        return new TimelineEventResponse(
                auditLog.getFromStatus(),
                auditLog.getToStatus(),
                auditLog.getMessage(),
                auditLog.getCreatedAt(),
                auditLog.getSequenceNumber()
        );
    }
}
