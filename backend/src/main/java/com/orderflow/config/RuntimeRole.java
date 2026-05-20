package com.orderflow.config;

import java.util.Locale;

/**
 * Runtime role used to split the API process from the asynchronous worker process.
 */
public enum RuntimeRole {
    ALL,
    API,
    WORKER;

    /**
     * Parses a configured role name.
     *
     * @param value configured role value
     * @return matching runtime role
     */
    public static RuntimeRole from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }

        return RuntimeRole.valueOf(value.trim().replace("-", "_").toUpperCase(Locale.ROOT));
    }
}
