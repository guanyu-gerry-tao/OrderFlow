package com.orderflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.api.ApiExceptionHandler;
import com.orderflow.dlq.DeadLetterController;
import com.orderflow.events.KafkaEventBroker;
import com.orderflow.events.KafkaOrderEventListener;
import com.orderflow.events.OrderEventConsumer;
import com.orderflow.events.OrderEventFailureHandler;
import com.orderflow.events.OrderEventProcessor;
import com.orderflow.events.OrderEventRetryScheduler;
import com.orderflow.events.RecordingEventBroker;
import com.orderflow.inventory.InventoryController;
import com.orderflow.operations.OperationsController;
import com.orderflow.order.OrderController;
import com.orderflow.outbox.OutboxFailureHandler;
import com.orderflow.outbox.OutboxPublisher;
import com.orderflow.outbox.OutboxPublisherScheduler;
import com.orderflow.realtime.RealtimeEventController;
import com.orderflow.realtime.RealtimeEventService;
import org.junit.jupiter.api.Test;

class RuntimeRoleOwnershipTest {

    @Test
    void apiEndpointsAreOwnedByApiRole() {
        assertRuntimeRole(ApiExceptionHandler.class, RuntimeRole.API);
        assertRuntimeRole(OrderController.class, RuntimeRole.API);
        assertRuntimeRole(InventoryController.class, RuntimeRole.API);
        assertRuntimeRole(DeadLetterController.class, RuntimeRole.API);
        assertRuntimeRole(OperationsController.class, RuntimeRole.API);
        assertRuntimeRole(RealtimeEventController.class, RuntimeRole.API);
        assertRuntimeRole(RealtimeEventService.class, RuntimeRole.API);
    }

    @Test
    void asynchronousWorkersAreOwnedByWorkerRole() {
        assertRuntimeRole(OutboxPublisher.class, RuntimeRole.WORKER);
        assertRuntimeRole(OutboxPublisherScheduler.class, RuntimeRole.WORKER);
        assertRuntimeRole(KafkaOrderEventListener.class, RuntimeRole.WORKER);
        assertRuntimeRole(KafkaEventBroker.class, RuntimeRole.WORKER);
        assertRuntimeRole(RecordingEventBroker.class, RuntimeRole.WORKER);
        assertRuntimeRole(OrderEventConsumer.class, RuntimeRole.WORKER);
        assertRuntimeRole(OrderEventProcessor.class, RuntimeRole.WORKER);
        assertRuntimeRole(OrderEventFailureHandler.class, RuntimeRole.WORKER);
        assertRuntimeRole(OutboxFailureHandler.class, RuntimeRole.WORKER);
        assertRuntimeRole(OrderEventRetryScheduler.class, RuntimeRole.WORKER);
    }

    private void assertRuntimeRole(Class<?> type, RuntimeRole role) {
        ConditionalOnRuntimeRole annotation = type.getAnnotation(ConditionalOnRuntimeRole.class);

        assertThat(annotation)
                .as("%s should declare runtime role ownership", type.getSimpleName())
                .isNotNull();
        assertThat(annotation.value())
                .as("%s should be owned by %s", type.getSimpleName(), role)
                .contains(role);
    }
}
