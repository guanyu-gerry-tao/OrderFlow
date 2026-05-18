package com.orderflow.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides whether the workflow should run directly or through the outbox path.
 */
@Component
public class EventMode {

    private final String mode;

    /**
     * Creates the mode reader from Spring configuration.
     *
     * @param mode configured event mode
     */
    public EventMode(@Value("${orderflow.events.mode:outbox-kafka}") String mode) {
        this.mode = mode;
    }

    /**
     * Returns true when the default reliable outbox path is enabled.
     *
     * @return whether outbox processing is enabled
     */
    public boolean isOutboxKafka() {
        return "outbox-kafka".equalsIgnoreCase(mode);
    }

    /**
     * Returns true when the direct baseline path is enabled.
     *
     * @return whether direct processing is enabled
     */
    public boolean isDirect() {
        return "direct".equalsIgnoreCase(mode) || "direct-consumer".equalsIgnoreCase(mode);
    }

    /**
     * Returns the configured mode name for health and troubleshooting responses.
     *
     * @return configured event mode
     */
    public String getModeName() {
        return mode;
    }
}
