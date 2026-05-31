package com.apiscope.core.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scans all {@code @RestController} endpoints on {@link ContextRefreshedEvent}
 * and publishes an {@link ApiScanCompletedEvent} for downstream ingestors.
 */
@Component
public class ApiMetadataScanner implements EndpointRepository {

    private static final Logger log = LoggerFactory.getLogger(ApiMetadataScanner.class);

    private static final List<String> INTERNAL_PACKAGES = List.of(
            "com.apiscope.core",
            "com.apiscope.autoconfigure",
            "com.apiscope.flow"
    );

    private final RequestMappingHandlerMapping handlerMapping;
    private final ApplicationEventPublisher eventPublisher;
    private final AtomicBoolean scanned = new AtomicBoolean(false);
    private volatile List<ApiEndpointMetadata> scannedEndpoints = List.of();

    public ApiMetadataScanner(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
            ApplicationEventPublisher eventPublisher) {
        this.handlerMapping = handlerMapping;
        this.eventPublisher = eventPublisher;
    }

    @EventListener(ContextRefreshedEvent.class)
    @Order(1)
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!scanned.compareAndSet(false, true)) return;

        List<ApiEndpointMetadata> endpoints = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(e -> isUserController(e.getValue().getBeanType()))
                .map(e -> toMetadata(e.getKey(), e.getValue()))
                .filter(e -> !"/unknown".equals(e.path()))
                .toList();

        this.scannedEndpoints = List.copyOf(endpoints);
        log.info("[APIScope] Scanned {} REST endpoints.", endpoints.size());
        eventPublisher.publishEvent(new ApiScanCompletedEvent(this, endpoints));
    }

    @Override
    public List<ApiEndpointMetadata> getScannedEndpoints() {
        return scannedEndpoints;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean isUserController(Class<?> beanType) {
        return beanType.isAnnotationPresent(org.springframework.web.bind.annotation.RestController.class)
                && INTERNAL_PACKAGES.stream().noneMatch(beanType.getName()::startsWith);
    }

    private ApiEndpointMetadata toMetadata(RequestMappingInfo info, HandlerMethod hm) {
        Method method = hm.getMethod();
        return new ApiEndpointMetadata(
                extractPath(info),
                extractHttpMethod(info),
                hm.getBeanType().getSimpleName(),
                method.getName(),
                extractDescription(method),
                extractPathParams(hm),
                extractRequiredQueryParams(hm),
                extractOptionalQueryParams(hm),
                extractRequestBodyType(hm),
                extractResponseType(hm)
        );
    }

    private String extractPath(RequestMappingInfo info) {
        var patternValues = info.getPatternValues();
        if (patternValues != null && !patternValues.isEmpty()) return patternValues.iterator().next();
        var condition = info.getPathPatternsCondition();
        if (condition != null && !condition.getPatterns().isEmpty())
            return condition.getPatterns().iterator().next().toString();
        return "/unknown";
    }

    private String extractHttpMethod(RequestMappingInfo info) {
        var methods = info.getMethodsCondition().getMethods();
        return methods.isEmpty() ? "GET" : methods.iterator().next().name();
    }

    private List<String> extractPathParams(HandlerMethod hm) {
        return Arrays.stream(hm.getMethodParameters())
                .filter(mp -> mp.hasParameterAnnotation(PathVariable.class))
                .map(mp -> resolveParamName(mp, mp.getParameterAnnotation(PathVariable.class).value(),
                                               mp.getParameterAnnotation(PathVariable.class).name()))
                .toList();
    }

    private List<String> extractRequiredQueryParams(HandlerMethod hm) {
        return Arrays.stream(hm.getMethodParameters())
                .filter(mp -> mp.hasParameterAnnotation(RequestParam.class))
                .filter(this::isRequired)
                .map(mp -> resolveParamName(mp, mp.getParameterAnnotation(RequestParam.class).value(),
                                               mp.getParameterAnnotation(RequestParam.class).name()))
                .toList();
    }

    private List<String> extractOptionalQueryParams(HandlerMethod hm) {
        return Arrays.stream(hm.getMethodParameters())
                .filter(mp -> mp.hasParameterAnnotation(RequestParam.class))
                .filter(mp -> !isRequired(mp))
                .map(mp -> resolveParamName(mp, mp.getParameterAnnotation(RequestParam.class).value(),
                                               mp.getParameterAnnotation(RequestParam.class).name()))
                .toList();
    }

    private boolean isRequired(MethodParameter mp) {
        RequestParam ann = mp.getParameterAnnotation(RequestParam.class);
        if (ann == null) return false;
        return ann.required() && ValueConstants.DEFAULT_NONE.equals(ann.defaultValue());
    }

    /**
     * Resolves a parameter name from annotation value/name attributes first,
     * then falls back to the compiled parameter name (requires -parameters flag).
     */
    private String resolveParamName(MethodParameter mp, String annotationValue, String annotationName) {
        if (!annotationValue.isBlank()) return annotationValue;
        if (!annotationName.isBlank())  return annotationName;

        // Fall back to compiled parameter name
        mp.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
        String discovered = mp.getParameterName();
        return discovered != null ? discovered : "param" + mp.getParameterIndex();
    }

    private String extractRequestBodyType(HandlerMethod hm) {
        return Arrays.stream(hm.getMethodParameters())
                .filter(mp -> mp.hasParameterAnnotation(RequestBody.class))
                .map(mp -> mp.getParameterType().getSimpleName())
                .findFirst().orElse(null);
    }

    private String extractResponseType(HandlerMethod hm) {
        Method method = hm.getMethod();
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class || returnType == Void.class) return "void";
        if ("ResponseEntity".equals(returnType.getSimpleName())
                && method.getGenericReturnType() instanceof ParameterizedType pt
                && pt.getActualTypeArguments().length > 0) {
            String typeName = pt.getActualTypeArguments()[0].getTypeName();
            return typeName.substring(typeName.lastIndexOf('.') + 1).replace(">", "");
        }
        return returnType.getSimpleName();
    }

    /** Reads @Operation(summary/description) if SpringDoc is present, otherwise converts the method name. */
    @SuppressWarnings("unchecked")
    protected String extractDescription(Method method) {
        try {
            Class<java.lang.annotation.Annotation> opClass =
                    (Class<java.lang.annotation.Annotation>) Class.forName("io.swagger.v3.oas.annotations.Operation");
            java.lang.annotation.Annotation ann = method.getAnnotation(opClass);
            if (ann != null) {
                for (String attr : List.of("summary", "description")) {
                    String val = (String) opClass.getMethod(attr).invoke(ann);
                    if (val != null && !val.isBlank()) return val;
                }
            }
        } catch (Exception ignored) {}
        return camelToSentence(method.getName());
    }

    /** Converts camelCase to a title-cased sentence: {@code "getById"} → {@code "Get By Id"}. */
    private static String camelToSentence(String name) {
        if (name == null || name.isBlank()) return name;
        String spaced = name.replaceAll("([A-Z])", " $1").trim();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
