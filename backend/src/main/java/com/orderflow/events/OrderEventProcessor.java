package com.orderflow.events;

import com.orderflow.audit.OrderAuditLogRepository;
import com.orderflow.audit.OrderAuditService;
import com.orderflow.inventory.InventoryService;
import com.orderflow.order.OrderEntity;
import com.orderflow.order.OrderItemEntity;
import com.orderflow.order.OrderRepository;
import com.orderflow.order.OrderStateMachine;
import com.orderflow.order.OrderStatus;
import com.orderflow.outbox.OutboxEvent;
import com.orderflow.outbox.OutboxEventRepository;
import com.orderflow.outbox.OutboxService;
import com.orderflow.payment.PaymentService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies published order events to the order workflow.
 */
@Service
public class OrderEventProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final OrderAuditService orderAuditService;
    private final OrderAuditLogRepository orderAuditLogRepository;
    private final OutboxService outboxService;
    private final OrderStateMachine orderStateMachine;

    /**
     * Creates the order event processor.
     *
     * @param outboxEventRepository outbox repository
     * @param orderRepository order repository
     * @param inventoryService inventory service
     * @param paymentService payment service
     * @param orderAuditService audit service
     * @param orderAuditLogRepository audit repository
     * @param outboxService outbox writer
     */
    public OrderEventProcessor(
            OutboxEventRepository outboxEventRepository,
            OrderRepository orderRepository,
            InventoryService inventoryService,
            PaymentService paymentService,
            OrderAuditService orderAuditService,
            OrderAuditLogRepository orderAuditLogRepository,
            OutboxService outboxService
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.orderAuditService = orderAuditService;
        this.orderAuditLogRepository = orderAuditLogRepository;
        this.outboxService = outboxService;
        this.orderStateMachine = new OrderStateMachine();
    }

    /**
     * Processes one outbox event inside a workflow transaction.
     *
     * @param eventId outbox event id
     */
    @Transactional
    public void processEvent(UUID eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found"));
        OrderEntity order = orderRepository.findById(event.getAggregateId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // Handle each event idempotently so duplicate broker delivery is safe.
        if (event.getEventType() == OrderEventType.ORDER_CREATED) {
            processOrderCreated(order, event);
        } else if (event.getEventType() == OrderEventType.INVENTORY_RESERVED) {
            processInventoryReserved(order, event);
        } else {
            throw new IllegalArgumentException("Unsupported order event type: " + event.getEventType());
        }
    }

    private void processOrderCreated(OrderEntity order, OutboxEvent event) {
        if (order.getStatus() != OrderStatus.CREATED) {
            event.markProcessed();
            return;
        }

        // Reserve inventory before moving the order forward.
        for (OrderItemEntity item : order.getItems()) {
            inventoryService.reserve(item.getSku(), item.getQuantity());
        }
        transition(order, OrderStatus.INVENTORY_RESERVED, "Inventory reserved");
        outboxService.enqueueOrderEvent(order.getId(), OrderEventType.INVENTORY_RESERVED);
        event.markProcessed();
    }

    private void processInventoryReserved(OrderEntity order, OutboxEvent event) {
        if (order.getStatus() == OrderStatus.COMPLETED) {
            event.markProcessed();
            return;
        }
        if (order.getStatus() != OrderStatus.INVENTORY_RESERVED) {
            throw new IllegalStateException("Order is not ready for payment authorization");
        }

        // Authorize payment and complete the workflow.
        paymentService.authorize(order);
        transition(order, OrderStatus.PAYMENT_AUTHORIZED, "Payment authorized");
        transition(order, OrderStatus.COMPLETED, "Order completed");
        event.markProcessed();
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
}
