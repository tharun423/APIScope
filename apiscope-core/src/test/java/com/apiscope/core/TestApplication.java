package com.apiscope.core;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application stub used exclusively by {@code @WebMvcTest}
 * and {@code @SpringBootTest} tests in the core module.
 *
 * <p>{@code @WebMvcTest} requires a {@code @SpringBootApplication}-annotated class
 * on the classpath to bootstrap the Spring MVC context. Since the core module is
 * a library (not a runnable app), this class serves as that stub for tests only.</p>
 *
 * <p>This class is in {@code src/test/java} — it is never packaged into the library JAR.</p>
 */
@SpringBootApplication
public class TestApplication {
    // No main method — this is a test-only Spring Boot context anchor.
}
