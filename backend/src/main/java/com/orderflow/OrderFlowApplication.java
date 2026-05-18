package com.orderflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Starts the OrderFlow backend application.
 */
@SpringBootApplication
@EnableScheduling
public class OrderFlowApplication {

    /**
     * Runs the Spring Boot application.
     *
     * @param args command-line arguments passed by the runtime
     */
    public static void main(String[] args) {
        SpringApplication.run(OrderFlowApplication.class, args);
    }
}
