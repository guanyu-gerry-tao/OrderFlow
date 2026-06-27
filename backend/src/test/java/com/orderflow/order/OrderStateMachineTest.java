package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderStateMachineTest {

    @Test
    void allowsTheHappyPathTransitions() {
        OrderStateMachine stateMachine = new OrderStateMachine();

        assertThatCode(() -> stateMachine.validateTransition(OrderStatus.PENDING_PAYMENT, OrderStatus.CREATED))
                .doesNotThrowAnyException();
        assertThatCode(() -> stateMachine.validateTransition(OrderStatus.CREATED, OrderStatus.INVENTORY_RESERVED))
                .doesNotThrowAnyException();
        assertThatCode(() -> stateMachine.validateTransition(OrderStatus.INVENTORY_RESERVED, OrderStatus.PAYMENT_AUTHORIZED))
                .doesNotThrowAnyException();
        assertThatCode(() -> stateMachine.validateTransition(OrderStatus.PAYMENT_AUTHORIZED, OrderStatus.COMPLETED))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTransitionsThatSkipRequiredWorkflowSteps() {
        OrderStateMachine stateMachine = new OrderStateMachine();

        assertThatThrownBy(() -> stateMachine.validateTransition(OrderStatus.CREATED, OrderStatus.COMPLETED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREATED")
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void rejectsTransitionsOutOfTerminalStatuses() {
        OrderStateMachine stateMachine = new OrderStateMachine();

        assertThatThrownBy(() -> stateMachine.validateTransition(OrderStatus.COMPLETED, OrderStatus.CANCELLED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED")
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void allowsFailureAndCancellationBeforeCompletion() {
        OrderStateMachine stateMachine = new OrderStateMachine();

        assertThatCode(() -> stateMachine.validateTransition(OrderStatus.CREATED, OrderStatus.FAILED))
                .doesNotThrowAnyException();
        assertThatCode(() -> stateMachine.validateTransition(OrderStatus.INVENTORY_RESERVED, OrderStatus.CANCELLED))
                .doesNotThrowAnyException();
        assertThatCode(() -> stateMachine.validateTransition(OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED))
                .doesNotThrowAnyException();
    }
}
