package com.apiscope.flow.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.apiscope.flow.sql.FlowStatementInspector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "apiscope.flow", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableAspectJAutoProxy
@ComponentScan("com.apiscope.flow")
public class AgenticDocsFlowAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper flowObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public RestClient flowRestClient() {
        return RestClient.create();
    }

    @Configuration
    @ConditionalOnClass(name = "org.hibernate.resource.jdbc.spi.StatementInspector")
    static class HibernateConfig {

        @Bean
        public FlowStatementInspector flowStatementInspector() {
            return new FlowStatementInspector();
        }

        @Bean
        public HibernatePropertiesCustomizer flowHibernatePropertiesCustomizer(FlowStatementInspector inspector) {
            return props -> props.put("hibernate.session_factory.statement_inspector", inspector);
        }
    }
}
