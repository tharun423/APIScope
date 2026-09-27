package com.apiscope.autoconfigure;

import com.apiscope.core.config.AgenticDocsProperties;
import com.apiscope.flow.sql.FlowStatementInspector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "apiscope", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AgenticDocsProperties.class)
@EnableAspectJAutoProxy
@ComponentScan(basePackages = {"com.apiscope.core", "com.apiscope.flow"})
public class AgenticDocsAutoConfiguration {

    @Bean("flowObjectMapper")
    public ObjectMapper flowObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean("flowRestClient")
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
        public HibernatePropertiesCustomizer flowHibernateCustomizer(FlowStatementInspector inspector) {
            return props -> props.put("hibernate.session_factory.statement_inspector", inspector);
        }
    }
}
