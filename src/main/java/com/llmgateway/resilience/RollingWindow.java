package com.llmgateway.resilience;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * A time-based sliding-window counter backed by a Redis sorted set (ZSET).
 *
 * Data-structure / algorithm:
 *   - Each event is a ZSET member scored by its epoch-millis timestamp.
 *   - On every read/write we ZREMRANGEBYSCORE everything older than (now - window),
 *     which evicts expired events in O(log N + M).
 *   - ZCARD then gives the count over exactly the trailing window in O(1).
 *
 * Two parallel sets per subject (total calls, errors) yield a live error rate that
 * is correct across multiple gateway instances sharing the same Redis.
 */
@Component
public class RollingWindow {

    private final ReactiveStringRedisTemplate redis;

    public RollingWindow(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public record WindowStats(long total, long errors) {
        public double errorRate() {
            return total == 0 ? 0.0 : (double) errors / (double) total;
        }
    }

    /** Record one observation (success or error) for {@code subject}. */
    public Mono<Void> record(String subject, boolean error, Duration window) {
        long now = System.currentTimeMillis();
        String member = now + "-" + UUID.randomUUID();
        String totalKey = totalKey(subject);
        String errorKey = errorKey(subject);

        Mono<Void> recordTotal = redis.opsForZSet().add(totalKey, member, now)
                .then(evict(totalKey, now - window.toMillis()))
                .then(redis.expire(totalKey, window.multipliedBy(2)))
                .then();

        Mono<Void> recordError = error
                ? redis.opsForZSet().add(errorKey, member, now)
                    .then(evict(errorKey, now - window.toMillis()))
                    .then(redis.expire(errorKey, window.multipliedBy(2)))
                    .then()
                : Mono.empty();

        return recordTotal.then(recordError);
    }


    private Mono<Long> evict(String key, long cutoffMillis) {
        // remove all members with score strictly less than cutoff: range (-inf, cutoff)
        Range<Double> expired = Range.from(Range.Bound.<Double>unbounded())
                .to(Range.Bound.exclusive((double) cutoffMillis));
        return redis.opsForZSet()
                .removeRangeByScore(key, expired)
                .defaultIfEmpty(0L);
    }

    private String totalKey(String subject) { return "health:w:" + subject + ":total"; }
    private String errorKey(String subject) { return "health:w:" + subject + ":errors"; }
}
