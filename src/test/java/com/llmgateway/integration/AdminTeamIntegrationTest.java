package com.llmgateway.integration;

import com.llmgateway.model.Team;
import com.llmgateway.repository.TeamRepository;
import com.llmgateway.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Full coverage of AdminTeamController: list, get by id (+404), create (and proof the
 * new key authenticates end-to-end), and the three PATCH updates (+404).
 */
class AdminTeamIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TeamRepository teamRepository;

    private Team seedTeam() {
        Team team = new Team();
        team.setApiKey("sk-" + UUID.randomUUID());
        team.setName("Admin Test " + UUID.randomUUID());
        team.setAllowedModels("llama3.1");
        team.setRpmLimit(60);
        team.setTpmLimit(60_000);
        team.setLowPriorityRpm(20);
        team.setDailyBudgetUsd(new BigDecimal("5.000000"));
        team.setMonthlyBudgetUsd(new BigDecimal("100.000000"));
        team.setBudgetExhausted(false);
        team.setEnrichmentProfile("internal");
        team.setAlertThresholdPct(80);
        team.setCreatedAt(OffsetDateTime.now());
        team.setUpdatedAt(OffsetDateTime.now());
        return teamRepository.save(team).block();
    }

    @Test
    void listReturnsTeams() {
        seedTeam();
        webTestClient.get().uri("/admin/teams")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[0].id").exists();
    }

    @Test
    void getByIdReturnsTeam() {
        Team team = seedTeam();
        webTestClient.get().uri("/admin/teams/{id}", team.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(team.getId())
                .jsonPath("$.name").isEqualTo(team.getName());
    }

    @Test
    void getByIdReturns404ForUnknownTeam() {
        webTestClient.get().uri("/admin/teams/{id}", 999_999)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error").isEqualTo("not_found");
    }

    @Test
    void createReturns201AndTeamAuthenticates() {
        String key = "sk-created-" + UUID.randomUUID();
        webTestClient.post().uri("/admin/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"apiKey":"%s","name":"Created Team","allowedModels":["llama3.1"],
                         "rpmLimit":50,"tpmLimit":50000,"lowPriorityRpm":10,
                         "dailyBudgetUsd":1.0,"monthlyBudgetUsd":10.0,"enrichmentProfile":"internal"}
                        """.formatted(key))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").exists()
                .jsonPath("$.name").isEqualTo("Created Team")
                .jsonPath("$.allowedModels[0]").isEqualTo("llama3.1");

        // Proves the key persisted and is authenticatable: a request with this key for an
        // unknown model reaches the model-authorization stage (403), not auth (401).
        webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"no-such-model","messages":[{"role":"user","content":"hi"}],"stream":false}
                        """)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void updateLimitsPersistsNewValue() {
        Team team = seedTeam();
        webTestClient.patch().uri("/admin/teams/{id}/limits", team.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"rpmLimit\":7}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.rpmLimit").isEqualTo(7);

        // Confirm it was persisted (and the cache invalidated) via a fresh read.
        webTestClient.get().uri("/admin/teams/{id}", team.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.rpmLimit").isEqualTo(7);
    }

    @Test
    void updateBudgetClearsExhaustedFlag() {
        Team team = seedTeam();
        teamRepository.updateBudgetExhausted(team.getId(), true).block();

        webTestClient.patch().uri("/admin/teams/{id}/budget", team.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"dailyBudgetUsd\":25.0}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.budgetExhausted").isEqualTo(false);
    }

    @Test
    void updateAlertThresholdPersists() {
        Team team = seedTeam();
        webTestClient.patch().uri("/admin/teams/{id}/alert-threshold", team.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"alertThresholdPct\":50}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.alertThresholdPct").isEqualTo(50);
    }

    @Test
    void updateAlertThresholdReturns404ForUnknownTeam() {
        webTestClient.patch().uri("/admin/teams/{id}/alert-threshold", 999_999)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"alertThresholdPct\":50}")
                .exchange()
                .expectStatus().isNotFound();
    }
}
