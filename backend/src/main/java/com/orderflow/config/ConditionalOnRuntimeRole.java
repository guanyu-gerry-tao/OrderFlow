package com.orderflow.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

/**
 * Enables a bean only for the configured OrderFlow runtime role.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(RuntimeRoleCondition.class)
public @interface ConditionalOnRuntimeRole {

    /**
     * Runtime roles that may load the annotated bean.
     *
     * @return accepted runtime roles
     */
    RuntimeRole[] value();
}
