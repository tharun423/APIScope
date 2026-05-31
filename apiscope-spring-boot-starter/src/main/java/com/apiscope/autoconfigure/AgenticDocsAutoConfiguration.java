package com.apiscope.autoconfigure;

import com.apiscope.core.config.AgenticDocsProperties;
import com.apiscope.core.config.VectorStoreConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "apiscope", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AgenticDocsProperties.class)
@ComponentScan(
        basePackages = "com.apiscope.core",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = VectorStoreConfig.class
        )
)
@Import(AgenticDocsAutoConfiguration.VectorStoreRegistrar.class)
public class AgenticDocsAutoConfiguration {

    /**
     * Registers VectorStoreConfig only when SimpleVectorStore is on the classpath.
     * Kept as a separate inner class so the @ConditionalOnClass is evaluated
     * BEFORE Spring attempts to load VectorStoreConfig — preventing the
     * NoClassDefFoundError: SimpleVectorStore crash on apps without spring-ai-vector-store.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.ai.vectorstore.SimpleVectorStore")
    @Import(VectorStoreConfig.class)
    static class VectorStoreRegistrar {}
}
