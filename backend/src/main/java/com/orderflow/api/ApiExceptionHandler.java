package com.orderflow.api;

import com.orderflow.config.ConditionalOnRuntimeRole;
import com.orderflow.config.RuntimeRole;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Converts common workflow exceptions into explicit API responses.
 */
@RestControllerAdvice
@ConditionalOnRuntimeRole(RuntimeRole.API)
public class ApiExceptionHandler {

    /**
     * Converts rejected workflow operations into conflict responses.
     *
     * @param exception rejected operation exception
     * @return conflict response
     */
    @ExceptionHandler(WorkflowConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleWorkflowConflict(WorkflowConflictException exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("conflict", exception.getMessage()));
    }

    /**
     * Preserves explicit response status exceptions with a simple JSON body.
     *
     * @param exception response status exception
     * @return API error response
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        String code = exception.getStatusCode().toString().toLowerCase().replace(" ", "_");
        return ResponseEntity
                .status(exception.getStatusCode())
                .body(new ApiErrorResponse(code, exception.getReason()));
    }

    /**
     * Converts request validation failures into bad request responses.
     *
     * @param exception validation exception
     * @return invalid request response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(MethodArgumentNotValidException exception) {
        return ResponseEntity
                .badRequest()
                .body(new ApiErrorResponse("invalid_request", "Request validation failed"));
    }
}
