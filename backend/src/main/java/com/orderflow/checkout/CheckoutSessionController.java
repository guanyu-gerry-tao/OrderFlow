package com.orderflow.checkout;

import com.orderflow.config.ConditionalOnRuntimeRole;
import com.orderflow.config.RuntimeRole;
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
 * Provides checkout session APIs.
 */
@RestController
@RequestMapping("/api/checkout-sessions")
@ConditionalOnRuntimeRole(RuntimeRole.API)
public class CheckoutSessionController {

    private final CheckoutSessionService checkoutSessionService;

    /**
     * Creates a checkout session controller.
     *
     * @param checkoutSessionService checkout session service
     */
    public CheckoutSessionController(CheckoutSessionService checkoutSessionService) {
        this.checkoutSessionService = checkoutSessionService;
    }

    /**
     * Starts a checkout session.
     *
     * @param request checkout request
     * @return checkout session response
     */
    @PostMapping
    public ResponseEntity<CheckoutSessionResponse> createSession(
            @Valid @RequestBody CreateCheckoutSessionRequest request
    ) {
        CheckoutSessionResponse response = checkoutSessionService.createSession(request);
        return ResponseEntity
                .created(URI.create("/api/checkout-sessions/" + response.checkoutSessionId()))
                .body(response);
    }

    /**
     * Fetches a checkout session.
     *
     * @param checkoutSessionId checkout session id
     * @return checkout session response
     */
    @GetMapping("/{checkoutSessionId}")
    public CheckoutSessionResponse getSession(@PathVariable UUID checkoutSessionId) {
        return checkoutSessionService.getSession(checkoutSessionId);
    }

    /**
     * Confirms checkout payment.
     *
     * @param checkoutSessionId checkout session id
     * @param request confirm request
     * @return checkout session response
     */
    @PostMapping("/{checkoutSessionId}/confirm")
    public CheckoutSessionResponse confirm(
            @PathVariable UUID checkoutSessionId,
            @RequestBody ConfirmCheckoutRequest request
    ) {
        return checkoutSessionService.confirm(checkoutSessionId, request);
    }
}
