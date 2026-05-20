package com.orderflow.operations;

import com.orderflow.config.ConditionalOnRuntimeRole;
import com.orderflow.config.RuntimeRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides operations-console read APIs.
 */
@RestController
@RequestMapping("/api/operations")
@ConditionalOnRuntimeRole(RuntimeRole.API)
public class OperationsController {

    private final OperationsHealthService operationsHealthService;

    /**
     * Creates the operations controller.
     *
     * @param operationsHealthService health read service
     */
    public OperationsController(OperationsHealthService operationsHealthService) {
        this.operationsHealthService = operationsHealthService;
    }

    /**
     * Returns workflow health and recovery counters.
     *
     * @return health response
     */
    @GetMapping("/health")
    public OperationsHealthResponse getHealth() {
        return operationsHealthService.getHealth();
    }
}
