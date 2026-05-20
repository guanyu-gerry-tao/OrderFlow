package com.orderflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class RuntimeRoleConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RuntimeRoleTestConfiguration.class);

    @Test
    void apiRoleLoadsApiBeansOnly() {
        contextRunner
                .withPropertyValues("orderflow.runtime.role=api")
                .run(context -> {
                    assertThat(context).hasBean("apiBean");
                    assertThat(context).doesNotHaveBean("workerBean");
                });
    }

    @Test
    void workerRoleLoadsWorkerBeansOnly() {
        contextRunner
                .withPropertyValues("orderflow.runtime.role=worker")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("apiBean");
                    assertThat(context).hasBean("workerBean");
                });
    }

    @Test
    void defaultRoleKeepsExistingAllInOneRuntime() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("apiBean");
            assertThat(context).hasBean("workerBean");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class RuntimeRoleTestConfiguration {

        @Bean
        @ConditionalOnRuntimeRole(RuntimeRole.API)
        String apiBean() {
            return "api";
        }

        @Bean
        @ConditionalOnRuntimeRole(RuntimeRole.WORKER)
        Integer workerBean() {
            return 1;
        }
    }
}
