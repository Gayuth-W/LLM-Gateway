package com.llmgateway.integration;

import com.llmgateway.model.Team;
import com.llmgateway.repository.TeamRepository;
import com.llmgateway.support.AbstractIntegrationTest;
import com.llmgateway.support.WireMockOllama;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives a deliberately tiny RPM bucket past its limit and asserts the gateway starts
 * returning 429 with a Retry-After header. Budget is set high so this isolates the rate
 * limiter from budget enforcement.
 */
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TeamRepository teamRepository;

    private String apiKey;

    @BeforeEach
    void seedTinyRpmTeam() {
        WireMockOllama.stubDefaultSuccess(OLLAMA);
        apiKey = "sk-rl-" + UUID.randomUUID();

        Team team = new Team();
        team.setApiKey(apiKey);
        team.setName("RateLimit Test");
        team.setAllowedModels("llama3.2:1b");
        team.setRpmLimit(3);            // only 3 requests/min at HIGH priority
        team.setTpmLimit(1_000_000);    // effectively unlimited tokens
        team.setLowPriorityRpm(1);
        team.setDailyBudgetUsd(new BigDecimal("100.00"));   // big budget: not the constraint
        team.setMonthlyBudgetUsd(new BigDecimal("1000.00"));
        team.setBudgetExhausted(false);
        team.setEnrichmentProfile("internal");
        team.setAlertThresholdPct(80);
        team.setCreatedAt(OffsetDateTime.now());
        team.setUpdatedAt(OffsetDateTime.now());
        teamRepository.save(team).block();
    }

    @Test
    void returns429AfterBucketIsExhausted() {
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger throttled = new AtomicInteger();

        for (int i = 0; i < 8; i++) {
            int status = webTestClient.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-Priority", "high")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"model":"llama3.2:1b","messages":[{"role":"user","content":"Hi"}],"stream":false}
                            """)
                    .exchange()
                    .returnResult(String.class)
                    .getStatus()
                    .value();

            if (status == 200) ok.incrementAndGet();
            else if (status == 429) throttled.incrementAndGet();
        }

        // With a capacity of 3, a burst of 8 must produce some throttling.
        assertThat(throttled.get()).isGreaterThan(0);
        assertThat(ok.get()).isLessThanOrEqualTo(4); // capacity + at most slight refill
    }

    @Test
    void throttledResponseCarriesRetryAfter() {
        // Exhaust the bucket.
        for (int i = 0; i < 6; i++) {
            webTestClient.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-Priority", "high")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"model":"llama3.2:1b","messages":[{"role":"user","content":"Hi"}],"stream":false}
                            """)
                    .exchange();
        }

        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("X-Priority", "high")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"llama3.2:1b","messages":[{"role":"user","content":"Hi"}],"stream":false}
                        """)
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists("Retry-After")
                .expectBody()
                .jsonPath("$.error").isEqualTo("rate_limited");
    }
}
