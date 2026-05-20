package com.orderflow.config;

import java.util.Map;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches beans against the configured {@code orderflow.runtime.role}.
 */
public class RuntimeRoleCondition implements Condition {

    private static final String PROPERTY_NAME = "orderflow.runtime.role";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnRuntimeRole.class.getName());
        if (attributes == null) {
            return true;
        }

        RuntimeRole activeRole = RuntimeRole.from(context.getEnvironment().getProperty(PROPERTY_NAME, "all"));
        if (activeRole == RuntimeRole.ALL) {
            return true;
        }

        RuntimeRole[] acceptedRoles = (RuntimeRole[]) attributes.get("value");
        for (RuntimeRole acceptedRole : acceptedRoles) {
            if (acceptedRole == activeRole) {
                return true;
            }
        }

        return false;
    }
}
