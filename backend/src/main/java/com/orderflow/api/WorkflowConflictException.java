package com.orderflow.api;

/**
 * Represents an expected workflow rejection caused by current business state.
 */
public class WorkflowConflictException extends RuntimeException {

    /**
     * Creates a workflow conflict exception.
     *
     * @param message human-readable conflict explanation
     */
    public WorkflowConflictException(String message) {
        super(message);
    }
}
