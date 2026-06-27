package com.orderflow.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application time source so TTL-sensitive flows are testable.
 */
@Configuration
public class ClockConfig {

    /**
     * Returns the default application clock.
     *
     * @return UTC clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
