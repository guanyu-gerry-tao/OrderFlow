package com.orderflow.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orderflow.dlq.DeadLetterEventRepository;
import com.orderflow.inventory.InventoryItemRepository;
import com.orderflow.order.OrderRepository;
import com.orderflow.outbox.EventMode;
import com.orderflow.outbox.OutboxEventRepository;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.annotation.Transactional;

class OperationsHealthServiceTest {

    @Test
    void reportsDatabaseDownWithoutThrowingWhenCountersCannotBeLoaded() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        InventoryItemRepository inventoryItemRepository = mock(InventoryItemRepository.class);
        OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
        DeadLetterEventRepository deadLetterEventRepository = mock(DeadLetterEventRepository.class);
        OperationsHealthService service = new OperationsHealthService(
                orderRepository,
                inventoryItemRepository,
                outboxEventRepository,
                deadLetterEventRepository,
                new EventMode("direct"),
                "recording"
        );

        when(orderRepository.count()).thenThrow(new DataAccessResourceFailureException("database unavailable"));

        OperationsHealthResponse response = service.getHealth();

        assertThat(response.backendStatus()).isEqualTo("DEGRADED");
        assertThat(response.databaseStatus()).isEqualTo("DOWN");
        assertThat(response.orderCount()).isZero();
        assertThat(response.outboxCounts()).containsEntry("PENDING", 0L);
        assertThat(response.dlqCount()).isZero();
    }

    @Test
    void healthAggregationDoesNotStartAnOuterTransaction() throws NoSuchMethodException {
        Method getHealth = OperationsHealthService.class.getMethod("getHealth");

        assertThat(getHealth.isAnnotationPresent(Transactional.class)).isFalse();
    }
}
