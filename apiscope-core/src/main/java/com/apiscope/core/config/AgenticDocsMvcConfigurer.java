package com.apiscope.core.config;

import com.apiscope.core.ratelimit.RateLimitInterceptor;
import com.apiscope.core.ratelimit.RateLimiterService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AgenticDocsMvcConfigurer implements WebMvcConfigurer {

    private final AgenticDocsProperties props;
    private final RateLimiterService rateLimiter;

    public AgenticDocsMvcConfigurer(AgenticDocsProperties props, RateLimiterService rateLimiter) {
        this.props      = props;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(rateLimiter))
                .addPathPatterns("/apiscope/api/**")
                .excludePathPatterns("/apiscope/api/endpoints", "/apiscope/api/endpoint-metrics", "/apiscope/api/admin/reindex");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/apiscope/index.html");
        registry.addViewController("/apiscope").setViewName("forward:/apiscope/index.html");
        registry.addViewController("/apiscope/").setViewName("forward:/apiscope/index.html");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/apiscope/api/**")
                .allowedOrigins(props.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
