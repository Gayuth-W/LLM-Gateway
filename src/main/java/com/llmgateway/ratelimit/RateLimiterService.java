package com.llmgateway.ratelimit;

import com.llmgateway.model.Team;
import com.llmgateway.model.enums.RequestPriority;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.AsyncBucketProxy;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Distributed rate limiting with Bucket4j over Redis. The core algorithm is the
 * TOKEN BUCKET: each bucket refills continuously and a request is admitted only if it
 * can take the tokens it needs.
 *
 * Two enforced dimensions:
 *   - requests/min: priority selects the bucket. HIGH uses the team's main RPM bucket;
 *     LOW uses a smaller reserved bucket, so batch traffic can never starve interactive
 *     traffic of the main allowance.
 *   - tokens/min: a shared TPM bucket. We debit an ESTIMATE at admission, then reconcile
 *     to the real token count after the call (refunding or debiting the difference).
 */

/*
Request arrives
        |
        v
tryAdmit()
        |
        +---- consume RPM
        |
        +---- failed?
        |          |
        |         YES -> 429
        |
        +---- consume TPM
                   |
                   +---- failed?
                   |          |
                   |          +---- refund RPM
                   |          +---- 429
                   |
                   +---- success
                              |
                              v
                        Call LLM
                              |
                              v
                  actual token count known
                              |
                              v
                 reconcileTokenUsage()
                              |
             +----------------+----------------+
             |                                 |
      actual > estimate                 estimate > actual
             |                                 |
     consumeIgnoringRateLimits()           addTokens()

 */

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final AsyncProxyManager<String> proxyManager;


    /**
     * Correct the TPM bucket after the real token count is known. Surplus is debited
     * (may push the bucket negative, throttling the next minute); over-estimates are refunded.
     */
    public Mono<Void> reconcileTokenUsage(Team team, int estimatedTokens, long actualTokens) {
        long delta = actualTokens - Math.max(1, estimatedTokens);
        if (delta == 0) {
            return Mono.empty();
        }
        String tpmKey = BucketKeys.tpm(team.getId());
        BucketConfiguration tpmCfg = perMinute(team.getTpmLimit());
        AsyncBucketProxy bucket = bucket(tpmKey, tpmCfg);
        if (delta > 0) {
            return Mono.fromFuture(() -> bucket.consumeIgnoringRateLimits(delta)).then()
                    .onErrorResume(e -> { log.warn("TPM surplus debit failed", e); return Mono.empty(); });
        }
        return Mono.fromFuture(() -> bucket.addTokens(-delta))
                .onErrorResume(e -> { log.warn("TPM refund failed", e); return Mono.empty(); });
    }

    
    // ---- Bucket4j plumbing ----

    private Mono<ConsumptionProbe> consume(String key, BucketConfiguration cfg, long tokens) {
        AsyncBucketProxy bucket = bucket(key, cfg);

        //creating of fetching if exists the rate-limit bucket
        return Mono.fromFuture(() -> bucket.tryConsumeAndReturnRemaining(tokens));
    }

    private Mono<Void> refund(String key, BucketConfiguration cfg, long tokens) {
        AsyncBucketProxy bucket = bucket(key, cfg);
        return Mono.fromFuture(() -> bucket.addTokens(tokens))
                .onErrorResume(e -> { log.warn("Refund failed for {}", key, e); return Mono.empty(); });
    }

    private AsyncBucketProxy bucket(String key, BucketConfiguration cfg) {
        // Bucket4j 8.7.0 async builder: build(key, configuration) takes the configuration
        // directly (the other overload expects a Supplier<CompletableFuture<...>>).
        // The configuration is applied only when the bucket is first created in Redis.
        return proxyManager.builder().build(key, cfg);
    }

    // Bucket4j configurations
    private BucketConfiguration perMinute(long limit) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(limit)
                .refillGreedy(limit, Duration.ofMinutes(1))//refill the bucket tokens every one minute, refill greedy fills it continuously rather than at once
                .build();
        return BucketConfiguration.builder()
                .addLimit(bandwidth)
                .build();
    }
}
