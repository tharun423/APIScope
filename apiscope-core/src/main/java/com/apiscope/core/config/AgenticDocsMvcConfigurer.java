package com.apiscope.core.config;

import com.apiscope.core.ratelimit.RateLimitInterceptor;
import com.apiscope.core.ratelimit.RateLimiterService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration: registers the rate-limit interceptor, UI view controllers,
 * and CORS rules for all {@code /apiscope/api/**} endpoints.
 */
@Configuration
public class AgenticDocsMvcConfigurer implements WebMvcConfigurer {

    private final AgenticDocsProperties properties;
    private final RateLimiterService rateLimiterService;

    public AgenticDocsMvcConfigurer(AgenticDocsProperties properties,
                                    RateLimiterService rateLimiterService) {
        this.properties       = properties;
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(rateLimiterService))
                .addPathPatterns("/apiscope/api/**")
                .excludePathPatterns(
                        "/apiscope/api/endpoints",
                        "/apiscope/api/endpoint-metrics",
                        "/apiscope/api/admin/reindex"
                );
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName(
                "forward:/apiscope/index.html");
        registry.addViewController("/apiscope").setViewName(
                "forward:/apiscope/index.html");
        registry.addViewController("/apiscope/").setViewName(
                "forward:/apiscope/index.html");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = properties.cors().allowedOrigins().toArray(String[]::new);
        registry.addMapping("/apiscope/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
