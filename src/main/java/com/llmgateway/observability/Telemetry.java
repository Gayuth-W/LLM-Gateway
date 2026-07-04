package com.llmgateway.observability;

import org.springframework.stereotype.Component;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import reactor.core.publisher.Mono;

/**
 * Thin helper over Micrometer Tracing. Incoming requests and the outbound Ollama
 * WebClient call are auto-instrumented; this adds gateway-specific attributes to the
 * current span and lets us wrap a stage (e.g. provider selection) in a child span.
 *
 * Tracer is optional: if tracing is disabled these calls become no-ops.
 */
@Component
public class Telemetry {

    private final Tracer tracer;

    public Telemetry(Tracer tracer) {
        this.tracer = tracer;
    }

    public void tag(String key, String value) {
        if (tracer == null || value == null) {
            return;
        }
        Span span = tracer.currentSpan();
        if (span != null) {
            span.tag(key, value);
        }
    }

    public void tag(String key, long value) {
        tag(key, Long.toString(value));
    }

    /** Wrap a reactive stage in a child span that closes when the Mono terminates. */
    public <T> Mono<T> traceChild(String name, Mono<T> inner) {
        if (tracer == null) {
            return inner;
        }
        return Mono.defer(() -> {
            Span span = tracer.nextSpan().name(name).start();
            try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                return inner.doOnError(span::error)
                        .doFinally(signal -> span.end());
            }
        });
    }
}
