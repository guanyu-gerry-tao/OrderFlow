package com.orderflow.operations;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.dlq.DeadLetterEventRepository;
import com.orderflow.inventory.InventoryItemRepository;
import com.orderflow.order.OrderRepository;
import com.orderflow.outbox.EventMode;
import com.orderflow.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OperationsController.class)
@Import(OperationsHealthService.class)
class OperationsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private InventoryItemRepository inventoryItemRepository;

    @MockitoBean
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private DeadLetterEventRepository deadLetterEventRepository;

    @MockitoBean
    private EventMode eventMode;

    @Test
    void returnsDegradedHealthResponseWhenDatabaseCountersFail() throws Exception {
        when(eventMode.getModeName()).thenReturn("direct");
        when(eventMode.isOutboxKafka()).thenReturn(false);
        when(orderRepository.count()).thenThrow(new DataAccessResourceFailureException("database unavailable"));

        mockMvc.perform(get("/api/operations/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backendStatus").value("DEGRADED"))
                .andExpect(jsonPath("$.databaseStatus").value("DOWN"))
                .andExpect(jsonPath("$.orderCount").value(0))
                .andExpect(jsonPath("$.outboxCounts.PENDING").value(0));
    }
}
