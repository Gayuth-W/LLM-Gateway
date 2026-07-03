package com.llmgateway.resilience;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.llmgateway.config.GatewayProperties;
import com.llmgateway.dto.admin.ProviderHealthView;
import com.llmgateway.model.ProviderHealthEvent;
import com.llmgateway.model.enums.ProviderStatus;
import com.llmgateway.provider.LLMProvider;
import com.llmgateway.provider.ProviderRegistry;
import com.llmgateway.repository.ProviderHealthEventRepository;
import com.llmgateway.service.AlertService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Periodically probes every known model and classifies it HEALTHY / DEGRADED / DOWN
 * from a rolling error-rate window. Real request outcomes feed the same window, so the
 * classification reflects live traffic, not just synthetic pings.
 *
 *   - error rate over the window  -> {@link RollingWindow} (Redis ZSET sliding window)
 *   - p50/p95/p99 latency         -> {@link LatencyRingBuffer} (in-memory ring buffer)
 *
 * Status transitions are persisted and alerted.
 */
@Service
public class ProviderHealthService {

    private static final Logger log = LoggerFactory.getLogger(ProviderHealthService.class);
    private static final int RING_CAPACITY = 256;
    private static final long MIN_SAMPLES = 3; // avoid flapping on a single failure

    private final ProviderRegistry registry;
    private final RollingWindow rollingWindow;
    private final ProviderHealthEventRepository healthRepo;
    private final AlertService alertService;
    private final GatewayProperties props;

    private final Map<String, ProviderStatus> statuses = new ConcurrentHashMap<>();
    private final Map<String, LatencyRingBuffer> latencies = new ConcurrentHashMap<>();

    public ProviderHealthService(ProviderRegistry registry,
                                 RollingWindow rollingWindow,
                                 ProviderHealthEventRepository healthRepo,
                                 AlertService alertService,
                                 GatewayProperties props) {
        this.registry = registry;
        this.rollingWindow = rollingWindow;
        this.healthRepo = healthRepo;
        this.alertService = alertService;
        this.props = props;
    }
    
    //returns the duration of the rolling time window used to calculate the provder health metrics
    private Duration window() {
        return props.resilience().healthCheck().rollingWindow();
    }

    /** Current classification (defaults to HEALTHY until proven otherwise). */
    public ProviderStatus status(String model) {
        return statuses.getOrDefault(model, ProviderStatus.HEALTHY);
    }

    /** Scheduled probe of all known models. */
    @Scheduled(fixedRateString = "30000")
    public void checkAll() {
        Flux.fromIterable(registry.knownModels())
                .flatMap(this::checkModel)
                .onErrorContinue((e, o) -> log.warn("Health check error", e))
                .subscribe();
    }

    private Mono<Void> checkModel(String model) {
        LLMProvider provider = registry.forModel(model);
        if (provider == null) {
            return Mono.empty();
        }
        long start = System.nanoTime();
        return provider.ping(model)
                .then(Mono.defer(() -> recordOutcome(model, true, elapsedMs(start))))
                .onErrorResume(e -> recordOutcome(model, false, elapsedMs(start)));
    }

    /** Feed one observation (from a ping or a real request) into the health signals. */
    public Mono<Void> recordOutcome(String model, boolean success, long latencyMs) {
        ring(model).record(latencyMs);
        return rollingWindow.record(model, !success, window())
                .then(rollingWindow.stats(model, window()))
                .flatMap(stats -> evaluate(model, stats))
                .then();
    }

    //Recalculates the health status of a model based on the latest rolling statistics.
    private Mono<Void> evaluate(String model, RollingWindow.WindowStats stats) {
        ProviderStatus next = classify(stats);
        ProviderStatus previous = statuses.put(model, next);
        if (previous == null) {
            previous = ProviderStatus.HEALTHY;
        }
        if (previous == next) {
            return Mono.empty();
        }
        double p99 = ring(model).p99();
        log.info("Model {} health {} -> {} (errorRate={}, p99={}ms)", model, previous, next,
                String.format("%.2f", stats.errorRate()), String.format("%.0f", p99));
        ProviderHealthEvent event =
                new ProviderHealthEvent(model, previous.name(), next.name(), stats.errorRate(), p99);
        final ProviderStatus from = previous;
        return healthRepo.save(event)
                .then(alertService.providerStatusChanged(model, from.name(), next.name()))
                .onErrorResume(e -> { log.warn("Failed to persist health event", e); return Mono.empty(); });
    }

}
