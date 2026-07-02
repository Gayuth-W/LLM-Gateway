package com.llmgateway.budget;

import com.llmgateway.config.GatewayProperties;
import com.llmgateway.model.Team;
import com.llmgateway.repository.SpendingRecordRepository;
import com.llmgateway.repository.TeamRepository;
import com.llmgateway.service.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Two-layer budget tracking:
 *
 *   1. LIVE ENFORCEMENT (Redis): every request INCRBYFLOATs a per-team/day USD
 *      counter. This is the source of truth for "are we over budget right now" and
 *      is correct across instances. Threshold crossings (warn %, 100%) fire exactly
 *      once via Redis SETNX guards.
 *
 *   2. DURABLE ROLLUP (Postgres): per-request deltas are pushed onto an in-memory,
 *      lock-free queue (O(1), no DB write on the hot path). A scheduled flush drains
 *      and aggregates the queue, then UPSERTs into spending_records. Worst-case loss
 *      on crash is one flush interval of reporting data; live enforcement is unaffected.
 *
 *
 *
 * LLM Request Completes
 *         │
 *         ▼
 *   recordUsage()
 *         │
 *         ├── Queue spending delta in memory
 *         │
 *         └── Increment Redis daily spending
 *                     │
 *                     ▼
 *             evaluateThresholds()
 *                     │
 *          ┌──────────┴──────────┐
 *          │                     │
 *          ▼                     ▼
 *     Budget Warning       Budget Exhausted
 *          │                     │
 *          ▼                     ▼
 *    Send Warning Alert    Update DB + Send Alert
 *
 *
 *    Every N seconds
 *         │
 *         ▼
 *       flush()
 *         │
 *         ▼
 *    Drain queue
 *         │
 *         ▼
 * Aggregate spending records
 *         │
 *         ▼
 * UPSERT into PostgreSQL
 */
@Service
public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);

    private final ReactiveStringRedisTemplate redis;
    private final SpendingRecordRepository spendingRepo;
    private final TeamRepository teamRepo;
    private final AlertService alertService;
    private final GatewayProperties props;

    private final Queue<SpendDelta> flushQueue = new ConcurrentLinkedQueue<>();

    public BudgetService(ReactiveStringRedisTemplate redis,
                         SpendingRecordRepository spendingRepo,
                         TeamRepository teamRepo,
                         AlertService alertService,
                         GatewayProperties props) {
        this.redis = redis;
        this.spendingRepo = spendingRepo;
        this.teamRepo = teamRepo;
        this.alertService = alertService;
        this.props = props;
    }

    private record SpendDelta(Long teamId, LocalDate day, String model, String provider,
                              long inputTokens, long outputTokens, BigDecimal cost) {}

    private record FlushKey(Long teamId, LocalDate day, String model, String provider) {}

    private static final class Accum {
        long in;
        long out;
        BigDecimal cost = BigDecimal.ZERO;
    }

    /** Called after every successful completion. Updates live counter + queues durable delta. */
    public Mono<Void> recordUsage(Team team, String model, String provider,
                                  long inputTokens, long outputTokens, BigDecimal cost) {
        LocalDate day = LocalDate.now();
        flushQueue.offer(new SpendDelta(team.getId(), day, model, provider, inputTokens, outputTokens, cost));

        String key = aggKey(team.getId(), day);
        return redis.opsForValue().increment(key, cost.doubleValue())
                .flatMap(newTotal -> redis.expire(key, Duration.ofDays(2)).thenReturn(newTotal))
                .flatMap(newTotal -> evaluateThresholds(team, day, newTotal))
                .then();
    }

    /** Live spend for today, in USD. */
    public Mono<BigDecimal> spentToday(Long teamId) {
        return redis.opsForValue().get(aggKey(teamId, LocalDate.now()))
                .map(BigDecimal::new)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    //Checks whether spending crossed configured budget thresholds.
    private Mono<Void> evaluateThresholds(Team team, LocalDate day, double newTotal) {
        double budget = team.getDailyBudgetUsd() == null ? 0.0 : team.getDailyBudgetUsd().doubleValue();
        if (budget <= 0.0) {
            return Mono.empty();
        }
        if (newTotal >= budget) {
            return firstTime(key(team.getId(), day, "exhausted-alerted"))
                    .flatMap(first -> {
                        Mono<Void> markDb = teamRepo.updateBudgetExhausted(team.getId(), true).then();
                        if (first) {
                            return markDb.then(alertService.budgetExhausted(team, BigDecimal.valueOf(newTotal)));
                        }
                        return markDb;
                    });
        }
        double pct = newTotal / budget;
        if (pct * 100.0 >= props.budget().warnThresholdPct()) {
            return firstTime(key(team.getId(), day, "warned"))
                    .flatMap(first -> first
                            ? alertService.budgetWarning(team, BigDecimal.valueOf(newTotal), pct)
                            : Mono.empty());
        }
        return Mono.empty();
    }

    /** Atomically claims a one-shot flag; true only for the caller that set it. */
    private Mono<Boolean> firstTime(String flagKey) {
        return redis.opsForValue()
                .setIfAbsent(flagKey, "1", Duration.ofDays(2))
                .defaultIfEmpty(false);
    }

    /**
     * Drains the queue, aggregates by (team, day, model, provider), and UPSERTs.
     * Aggregation collapses many small deltas into one DB statement per bucket.
     */
    @Scheduled(fixedRateString = "${gateway.budget.flush-interval}")
    public void flush() {
        Map<FlushKey, Accum> aggregated = new HashMap<>();
        SpendDelta delta;
        int drained = 0;
        while ((delta = flushQueue.poll()) != null) {
            FlushKey k = new FlushKey(delta.teamId(), delta.day(), delta.model(), delta.provider());
            Accum a = aggregated.computeIfAbsent(k, x -> new Accum());
            a.in += delta.inputTokens();
            a.out += delta.outputTokens();
            a.cost = a.cost.add(delta.cost());
            drained++;
        }
        if (aggregated.isEmpty()) {
            return;
        }
        final int total = drained;
        Flux.fromIterable(aggregated.entrySet())
                .flatMap(e -> spendingRepo.upsertSpend(
                        e.getKey().teamId(), e.getKey().day(), e.getKey().model(), e.getKey().provider(),
                        e.getValue().in, e.getValue().out, e.getValue().cost))
                .then()
                .doOnError(err -> log.error("Spend flush failed; deltas dropped", err))
                .subscribe(v -> log.debug("Flushed {} spend deltas into {} buckets", total, aggregated.size()));
    }

    //Generates Redis key for total daily spending
    private String aggKey(Long teamId, LocalDate day) {
        return "budget:" + teamId + ":" + day;
    }


    //Generates special Redis keys.
    private String key(Long teamId, LocalDate day, String suffix) {
        return "budget:" + teamId + ":" + day + ":" + suffix;
    }
}
