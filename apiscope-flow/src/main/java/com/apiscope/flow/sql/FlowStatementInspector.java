package com.apiscope.flow.sql;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Hibernate {@link StatementInspector} that feeds every SQL statement into
 * {@link SqlCapture} so it can be associated with the current AOP-traced method.
 *
 * <p>This bean is registered with Hibernate via
 * {@code hibernate.session_factory.statement_inspector} only when
 * {@code hibernate-core} is present on the classpath (guarded by
 * {@code @ConditionalOnClass} in the auto-configuration).
 *
 * <p>When no Flow-Tracer capture session is active (i.e. a normal, non-traced
 * request), {@link SqlCapture#add} is a no-op, so there is zero overhead on
 * production traffic.
 */
public class FlowStatementInspector implements StatementInspector {

    @Override
    public String inspect(String sql) {
        SqlCapture.add(sql);
        return sql; // never modify — return unchanged to Hibernate
    }
}
