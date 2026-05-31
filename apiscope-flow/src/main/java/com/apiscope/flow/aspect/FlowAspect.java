package com.apiscope.flow.aspect;

import com.apiscope.flow.model.TraceEvent;
import com.apiscope.flow.serializer.TraceSerializer;
import com.apiscope.flow.spi.TraceEventSink;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring AOP aspect that intercepts public method calls on {@code @Service},
 * {@code @RestController}, and {@code @Repository} beans belonging to the host application
 * during a traced HTTP request.
 *
 * <p>A request is considered "traced" only if the incoming HTTP request carries the header
 * {@code X-Flow-Trace-Id}. This means all normal (non-traced) requests pass through with
 * zero overhead beyond a header check.
 *
 * <p>The aspect skips any class inside {@code com.apiscope} to avoid recursively
 * tracing its own internals.
 *
 * <p>Input args and return values are serialized to JSON (capped at 2 KB).
 * Non-serializable types fall back to {@link Object#toString()}.
 */
@Aspect
@Component
public class FlowAspect {

    /** Header name injected by FlowExecutorService on outbound RestClient calls. */
    public static final String TRACE_HEADER = "X-Flow-Trace-Id";

    private final TraceEventSink  sink;
    private final TraceSerializer serializer;

    /** Per-trace step counters so each step gets a unique ascending index. */
    private final Map<String, AtomicInteger> stepCounters = new ConcurrentHashMap<>();

    public FlowAspect(TraceEventSink sink, TraceSerializer serializer) {
        this.sink       = sink;
        this.serializer = serializer;
    }

    /**
     * Intercepts all public methods on @Service, @RestController, and @Repository beans,
     * excluding the flow/core/autoconfigure internals to prevent recursive self-tracing.
     */
    @Around("""
            (within(@org.springframework.stereotype.Service *)
             || within(@org.springframework.web.bind.annotation.RestController *)
             || within(@org.springframework.stereotype.Repository *))
            && !within(com.apiscope.flow..*)
            && !within(com.apiscope.core..*)
            && !within(com.apiscope.autoconfigure..*)
            """)
    public Object traceMethodCall(ProceedingJoinPoint pjp) throws Throwable {
        String traceId = resolveTraceId();
        if (traceId == null) {
            return pjp.proceed();
        }

        MethodSignature sig        = (MethodSignature) pjp.getSignature();
        String          className  = pjp.getTarget().getClass().getSimpleName();
        String          methodName = sig.getName();
        String          layer      = resolveLayer(pjp.getTarget());
        int             stepIndex  = nextStep(traceId);
        String inputJson = serializer.serializeArgs(pjp.getArgs());
        long   start     = System.currentTimeMillis();
        SqlCapture.begin();

        try {
            Object result = pjp.proceed();
            long durationMs = System.currentTimeMillis() - start;
            List<String> queries = SqlCapture.drain();
            sink.pushStep(traceId, new TraceEvent(
                    traceId, stepIndex, layer, className, methodName,
                    inputJson, serializer.serializeValue(result), durationMs, "EXIT", null, queries));
            return result;
        } catch (Throwable ex) {
            long durationMs = System.currentTimeMillis() - start;
            List<String> queries = SqlCapture.drain(); // always drain to prevent ThreadLocal leaks
            sink.pushStep(traceId, new TraceEvent(
                    traceId, stepIndex, layer, className, methodName,
                    inputJson, null, durationMs, "ERROR", serializer.buildErrorMessage(ex), queries));
            throw ex;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Read the X-Flow-Trace-Id header from the current request thread. Returns null for normal requests. */
    private String resolveTraceId() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest request = attrs.getRequest();
            return request.getHeader(TRACE_HEADER);
        } catch (Exception e) {
            return null;
        }
    }

    /** Determine the layer label from the bean's declared annotations. */
    private String resolveLayer(Object target) {
        Class<?> cls = target.getClass();
        if (cls.isAnnotationPresent(RestController.class)) return "CONTROLLER";
        if (cls.isAnnotationPresent(Service.class))        return "SERVICE";
        if (cls.isAnnotationPresent(Repository.class))     return "REPOSITORY";
        return "COMPONENT";
    }

    /** Get or create a step counter for the trace and return the next index. */
    private int nextStep(String traceId) {
        return stepCounters.computeIfAbsent(traceId, id -> new AtomicInteger(0))
                           .getAndIncrement();
    }

    /**
     * Returns the total number of steps recorded for the given trace and removes
     * the counter entry from the map (prevents unbounded memory growth).
     * Called by {@link com.apiscope.flow.executor.FlowExecutorService} just before
     * pushing the {@code done} event.
     */
    public int getAndClearStepCount(String traceId) {
        AtomicInteger counter = stepCounters.remove(traceId);
        return counter != null ? counter.get() : 0;
    }
}

