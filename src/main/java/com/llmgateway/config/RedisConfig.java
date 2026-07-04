package com.llmgateway.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wiring for the two Redis use-cases:
 *   1. Bucket4j distributed token buckets  -> a dedicated Lettuce connection with a
 *      {@code <String, byte[]>} codec (Bucket4j stores bucket state as raw bytes).
 *   2. Budget counters + rolling windows    -> Spring Boot's auto-configured
 *      {@code ReactiveStringRedisTemplate} (no bean needed here).
 *
 * NOTE: {@link LettuceBasedProxyManager} construction is the most version-sensitive
 * line in the project. This targets bucket4j-redis 8.7.0; if you bump the version,
 * re-check builderFor(...) and the expiration-strategy factory.
 */
@Configuration
public class RedisConfig {

    @Bean(destroyMethod = "shutdown")
    public RedisClient lettuceRedisClient(@Value("${spring.data.redis.host}") String host,
                                          @Value("${spring.data.redis.port}") int port) {
        return RedisClient.create(RedisURI.Builder.redis(host, port).build());
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> bucket4jRedisConnection(RedisClient client) {
        // Keys are human-readable strings (team:{id}:rpm); values are Bucket4j's binary state.
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        return client.connect(codec);
    }

    @Bean
    public LettuceBasedProxyManager<String> bucketProxyManager(StatefulRedisConnection<String, byte[]> connection) {
        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(5)))
                .build();
    }
}
