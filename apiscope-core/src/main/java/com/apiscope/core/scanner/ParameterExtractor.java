package com.apiscope.core.scanner;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;

/**
 * Extracts path params, query params, request body type, and response type
 * from a Spring MVC {@link HandlerMethod}.
 */
class ParameterExtractor {

    List<String> pathParams(HandlerMethod hm) {
        return Arrays.stream(hm.getMethodParameters())
                .filter(mp -> mp.hasParameterAnnotation(PathVariable.class))
                .map(mp -> resolveName(mp,
                        mp.getParameterAnnotation(PathVariable.class).value(),
                        mp.getParameterAnnotation(PathVariable.class).name()))
                .toList();
    }

    List<String> requiredQueryParams(HandlerMethod hm) {
        return Arrays.stream(hm.getMethodParameters())
                .filter(mp -> mp.hasParameterAnnotation(RequestParam.class))
                .filter(this::isRequired)
                .map(mp -> resolveName(mp,
                        mp.getParameterAnnotation(RequestParam.class).value(),
                        mp.getParameterAnnotation(RequestParam.class).name()))
                .toList();
    }

    List<String> optionalQueryParams(HandlerMethod hm) {
        return Arrays.stream(hm.getMethodParameters())
                .filter(mp -> mp.hasParameterAnnotation(RequestParam.class))
                .filter(mp -> !isRequired(mp))
                .map(mp -> resolveName(mp,
                        mp.getParameterAnnotation(RequestParam.class).value(),
                        mp.getParameterAnnotation(RequestParam.class).name()))
                .toList();
    }

    String requestBodyType(HandlerMethod hm) {
        return Arrays.stream(hm.getMethodParameters())
                .filter(mp -> mp.hasParameterAnnotation(RequestBody.class))
                .map(mp -> mp.getParameterType().getSimpleName())
                .findFirst().orElse(null);
    }

    String responseType(HandlerMethod hm) {
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

    private boolean isRequired(MethodParameter mp) {
        RequestParam ann = mp.getParameterAnnotation(RequestParam.class);
        return ann != null && ann.required() && ValueConstants.DEFAULT_NONE.equals(ann.defaultValue());
    }

    private String resolveName(MethodParameter mp, String value, String name) {
        if (!value.isBlank()) return value;
        if (!name.isBlank())  return name;
        mp.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
        String discovered = mp.getParameterName();
        return discovered != null ? discovered : "param" + mp.getParameterIndex();
    }
}
