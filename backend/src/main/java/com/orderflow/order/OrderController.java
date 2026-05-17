package com.orderflow.order;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides order workflow REST APIs.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderWorkflowService orderWorkflowService;

    /**
     * Creates an order controller.
     *
     * @param orderWorkflowService order workflow service
     */
    public OrderController(OrderWorkflowService orderWorkflowService) {
        this.orderWorkflowService = orderWorkflowService;
    }

    /**
     * Creates an order and runs the M1 synchronous happy path.
     *
     * @param request order creation request
     * @return created order response
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse orderResponse = orderWorkflowService.createOrder(request);
        return ResponseEntity
                .created(URI.create("/api/orders/" + orderResponse.orderId()))
                .body(orderResponse);
    }

    /**
     * Fetches one order by id.
     *
     * @param orderId order identifier
     * @return order response
     */
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable UUID orderId) {
        return orderWorkflowService.getOrder(orderId);
    }

    /**
     * Fetches the audit timeline for one order.
     *
     * @param orderId order identifier
     * @return timeline response
     */
    @GetMapping("/{orderId}/timeline")
    public TimelineResponse getTimeline(@PathVariable UUID orderId) {
        return orderWorkflowService.getTimeline(orderId);
    }
}
