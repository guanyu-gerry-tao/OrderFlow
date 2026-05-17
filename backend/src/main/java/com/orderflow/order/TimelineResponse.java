package com.orderflow.order;

import java.util.List;

/**
 * Public API response for an order timeline.
 *
 * @param events ordered timeline events
 */
public record TimelineResponse(List<TimelineEventResponse> events) {
}
