package com.llmgateway.integration;

import com.llmgateway.model.Team;
import com.llmgateway.repository.TeamRepository;
import com.llmgateway.support.AbstractIntegrationTest;
import com.llmgateway.support.WireMockOllama;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The ProxyController rejection paths not already covered by the sibling tests.
 * Auth (401), model authorization (403), fallback, streaming and rate limiting (429)
 * live in CompletionAndFallback / Streaming / RateLimit tests; this adds 402 (budget
 * exhausted) and 422 (content policy).
 */
class ProxyEndpointIntegrationTest extends AbstractIntegrationTest {

    private static final String CHAT = "/v1/chat/completions";

    @Autowired
    TeamRepository teamRepository;

    private Team seedTeam(String allowedModels, BigDecimal dailyBudget) {
        Team team = new Team();
        team.setApiKey("sk-" + UUID.randomUUID());
        team.setName("Proxy Test");
        team.setAllowedModels(allowedModels);
        team.setRpmLimit(1000);              // high: rate limit is not the constraint here
        team.setTpmLimit(100_000_000);
        team.setLowPriorityRpm(500);
        team.setDailyBudgetUsd(dailyBudget);
        team.setMonthlyBudgetUsd(new BigDecimal("1000.000000"));
        team.setBudgetExhausted(false);
        team.setEnrichmentProfile("internal");
        team.setAlertThresholdPct(80);
        team.setCreatedAt(OffsetDateTime.now());
        team.setUpdatedAt(OffsetDateTime.now());
        return teamRepository.save(team).block();
    }

    @Test
    void secondRequestIsRejectedWhenDailyBudgetExhausted() {
        // mistral has real (non-zero) pricing in the test config; a large token count makes
        // one request's cost clearly exceed the tiny daily budget.
        WireMockOllama.stubSuccess(OLLAMA, "mistral", "ok", 10_000, 10_000);
        String key = seedTeam("mistral", new BigDecimal("0.001000")).getApiKey();

        String body = """
                {"model":"mistral","messages":[{"role":"user","content":"hi"}],"stream":false}
                """;

        // First call succeeds and records the spend (well over the $0.001 cap).
        webTestClient.post().uri(CHAT)
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk();

        // Second call is now over budget.
        webTestClient.post().uri(CHAT)
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(402)
                .expectBody()
                .jsonPath("$.error").isEqualTo("budget_exhausted");
    }

    @Test
    void blankPromptIsRejectedByContentFilter() {
        // Budget is generous so the request reaches the content-filter stage.
        String key = seedTeam("llama3.1", new BigDecimal("10.000000")).getApiKey();

        webTestClient.post().uri(CHAT)
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"llama3.1","messages":[{"role":"user","content":""}],"stream":false}
                        """)
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.error").isEqualTo("content_policy_violation");
    }
}
