package com.llmgateway.resilience;

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
