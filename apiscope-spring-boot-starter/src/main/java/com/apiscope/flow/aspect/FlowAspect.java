package com.apiscope.flow.aspect;

import com.apiscope.flow.model.TraceEvent;
import com.apiscope.flow.registry.FlowSseRegistry;
import com.apiscope.flow.serializer.TraceSerializer;
import com.apiscope.flow.sql.SqlCapture;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Intercepts public methods on @Service, @RestController, @Repository beans
 * only when the request carries X-Flow-Trace-Id — zero overhead on normal traffic.
 */
@Aspect
@Component
public class FlowAspect {

    public static final String TRACE_HEADER = "X-Flow-Trace-Id";

    private final FlowSseRegistry registry;
    private final TraceSerializer serializer;
    private final Map<String, AtomicInteger> stepCounters = new ConcurrentHashMap<>();

    public FlowAspect(FlowSseRegistry registry, TraceSerializer serializer) {
        this.registry   = registry;
        this.serializer = serializer;
    }

    @Around("""
            (within(@org.springframework.stereotype.Service *)
             || within(@org.springframework.web.bind.annotation.RestController *)
             || within(@org.springframework.stereotype.Repository *))
            && !within(com.apiscope..*)
            """)
    public Object traceMethodCall(ProceedingJoinPoint pjp) throws Throwable {
        String traceId = currentTraceId();
        if (traceId == null) return pjp.proceed();

        String className  = pjp.getTarget().getClass().getSimpleName();
        String methodName = ((MethodSignature) pjp.getSignature()).getName();
        String layer      = layer(pjp.getTarget());
        int    step       = nextStep(traceId);
        String input      = serializer.serializeArgs(pjp.getArgs());
        long   start      = System.currentTimeMillis();
        SqlCapture.begin();

        try {
            Object result = pjp.proceed();
            registry.pushStep(traceId, new TraceEvent(
                    traceId, step, layer, className, methodName,
                    input, serializer.serializeValue(result),
                    System.currentTimeMillis() - start, "EXIT", null, SqlCapture.drain()));
            return result;
        } catch (Throwable ex) {
            registry.pushStep(traceId, new TraceEvent(
                    traceId, step, layer, className, methodName,
                    input, null,
                    System.currentTimeMillis() - start, "ERROR", serializer.errorMessage(ex), SqlCapture.drain()));
            throw ex;
        }
    }

    /** Called by FlowExecutorService after the HTTP call completes to free the counter. */
    public int getAndClearStepCount(String traceId) {
        AtomicInteger counter = stepCounters.remove(traceId);
        return counter != null ? counter.get() : 0;
    }

    /** Called on error path to prevent stepCounters memory leak. */
    public void clearStepCount(String traceId) {
        stepCounters.remove(traceId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String currentTraceId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest().getHeader(TRACE_HEADER) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String layer(Object target) {
        Class<?> cls = target.getClass();
        if (cls.isAnnotationPresent(RestController.class)) return "CONTROLLER";
        if (cls.isAnnotationPresent(Service.class))        return "SERVICE";
        if (cls.isAnnotationPresent(Repository.class))     return "REPOSITORY";
        return "COMPONENT";
    }

    private int nextStep(String traceId) {
        return stepCounters.computeIfAbsent(traceId, id -> new AtomicInteger(0)).getAndIncrement();
    }
}
