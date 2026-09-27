package com.apiscope.flow.sql;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/** Feeds every SQL statement into SqlCapture during a traced request. No-op otherwise. */
public class FlowStatementInspector implements StatementInspector {

    @Override
    public String inspect(String sql) {
        SqlCapture.add(sql);
        return sql;
    }
}
