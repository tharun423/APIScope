package com.apiscope.core.scanner;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;

@Component
public class EndpointMetadataExtractor {

    private final DescriptionResolver descriptionResolver;

    public EndpointMetadataExtractor(DescriptionResolver descriptionResolver) {
        this.descriptionResolver = descriptionResolver;
    }

    public ApiEndpointMetadata extract(RequestMappingInfo info, HandlerMethod hm) {
        Method method = hm.getMethod();
        return new ApiEndpointMetadata(
                path(info),
                httpMethod(info),
                hm.getBeanType().getSimpleName(),
                method.getName(),
                descriptionResolver.resolveBusinessLogic(method),
                pathParams(hm),
                requiredQueryParams(hm),
                optionalQueryParams(hm),
                requestBodyType(hm),
                responseType(hm)
        );
    }

    private String path(RequestMappingInfo info) {
        var patterns = info.getPatternValues();
        if (patterns != null && !patterns.isEmpty()) return patterns.iterator().next();
        var condition = info.getPathPatternsCondition();
        if (condition != null && !condition.getPatterns().isEmpty())
            return condition.getPatterns().iterator().next().toString();
        return "/unknown";
    }

    private String httpMethod(RequestMappingInfo info) {
        var methods = info.getMethodsCondition().getMethods();
        return methods.isEmpty() ? "GET" : methods.iterator().next().name();
    }

    private List<String> pathParams(HandlerMethod hm) {
        return Arrays.stream(hm.getMethodParameters())
                .filter(mp -> mp.hasParameterAnnotation(PathVariable.class))
                .map(mp -> resolveName(mp,
                        mp.getParameterAnnotation(PathVariable.class).value(),
                        mp.getParameterAnnotation(PathVariable.class).name()))
                .toList();
    }

    private List<String> requiredQueryParams(HandlerMethod hm) {
        return Arrays.stream(hm.getMethodParameters())
                .filter(mp -> mp.hasParameterAnnotation(RequestParam.class))
                .filter(this::isRequired)
                .map(mp -> resolveName(mp,
                        mp.getParameterAnnotation(RequestParam.class).value(),
                        mp.getParameterAnnotation(RequestParam.class).name()))
                .toList();
    }

    private List<String> optionalQueryParams(HandlerMethod hm) {
        return Arrays.stream(hm.getMethodParameters())
                .filter(mp -> mp.hasParameterAnnotation(RequestParam.class))
                .filter(mp -> !isRequired(mp))
                .map(mp -> resolveName(mp,
                        mp.getParameterAnnotation(RequestParam.class).value(),
                        mp.getParameterAnnotation(RequestParam.class).name()))
                .toList();
    }

    private boolean isRequired(MethodParameter mp) {
        RequestParam ann = mp.getParameterAnnotation(RequestParam.class);
        return ann != null && ann.required() && ValueConstants.DEFAULT_NONE.equals(ann.defaultValue());
    }

    private String resolveName(MethodParameter mp, String value, String name) {
        if (!value.isBlank()) return value;
        if (!name.isBlank())  return name;
        mp.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
        String found = mp.getParameterName();
        return found != null ? found : "param" + mp.getParameterIndex();
    }

    private String requestBodyType(HandlerMethod hm) {
        return Arrays.stream(hm.getMethodParameters())
                .filter(mp -> mp.hasParameterAnnotation(RequestBody.class))
                .map(mp -> mp.getParameterType().getSimpleName())
                .findFirst().orElse(null);
    }

    private String responseType(HandlerMethod hm) {
        Method method = hm.getMethod();
        Class<?> ret = method.getReturnType();
        if (ret == void.class || ret == Void.class) return "void";
        if ("ResponseEntity".equals(ret.getSimpleName())
                && method.getGenericReturnType() instanceof ParameterizedType pt
                && pt.getActualTypeArguments().length > 0) {
            String t = pt.getActualTypeArguments()[0].getTypeName();
            return t.substring(t.lastIndexOf('.') + 1).replace(">", "");
        }
        return ret.getSimpleName();
    }
}
