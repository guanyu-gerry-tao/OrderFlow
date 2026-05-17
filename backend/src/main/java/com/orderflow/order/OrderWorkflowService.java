package com.orderflow.order;

import com.orderflow.audit.OrderAuditLog;
import com.orderflow.audit.OrderAuditLogRepository;
import com.orderflow.audit.OrderAuditService;
import com.orderflow.inventory.InventoryService;
import com.orderflow.payment.PaymentService;
import java.util.List;
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

    /**
     * Creates an order workflow service.
     *
     * @param orderRepository order repository
     * @param inventoryService inventory reservation service
     * @param paymentService payment simulation service
     * @param orderAuditService audit writer
     * @param orderAuditLogRepository audit query repository
     */
    public OrderWorkflowService(
            OrderRepository orderRepository,
            InventoryService inventoryService,
            PaymentService paymentService,
            OrderAuditService orderAuditService,
            OrderAuditLogRepository orderAuditLogRepository
    ) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.orderAuditService = orderAuditService;
        this.orderAuditLogRepository = orderAuditLogRepository;
        this.orderStateMachine = new OrderStateMachine();
    }

    /**
     * Creates an order and completes the synchronous happy path.
     *
     * @param request order creation request
     * @return completed order response
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
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
