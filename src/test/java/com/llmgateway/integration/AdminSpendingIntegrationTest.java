package com.llmgateway.integration;

import com.llmgateway.model.SpendingRecord;
import com.llmgateway.model.Team;
import com.llmgateway.repository.SpendingRecordRepository;
import com.llmgateway.repository.TeamRepository;
import com.llmgateway.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Locks down GET /admin/spending and, crucially, the R2DBC result-to-record projection
 * that previously threw (the SQL aliases didn't line up with how R2DBC binds columns).
 * Rows are seeded straight into spending_records and asserted back through the report.
 *
 * Isolation: the flush interval is 1h in tests, so nothing auto-populates
 * spending_records -- only rows seeded here exist. Rows are still matched by a unique
 * team name so the assertions hold even if other spend-seeding tests are added later.
 */
class AdminSpendingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TeamRepository teamRepository;
    @Autowired
    SpendingRecordRepository spendingRepository;

    private Team seedTeam(String name) {
        Team team = new Team();
        team.setApiKey("sk-" + UUID.randomUUID());
        team.setName(name);
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

    private void seedSpend(Long teamId, String model, long in, long out, String cost) {
        SpendingRecord r = new SpendingRecord();
        r.setTeamId(teamId);
        r.setDay(LocalDate.now());
        r.setModel(model);
        r.setProvider("ollama");
        r.setInputTokens(in);
        r.setOutputTokens(out);
        r.setCostUsd(new BigDecimal(cost));
        spendingRepository.save(r).block();
    }

    @Test
    void spendingReportMapsEveryColumn() {
        String teamName = "Spending Co " + UUID.randomUUID();
        Team team = seedTeam(teamName);
        seedSpend(team.getId(), "llama3.1", 100, 50, "0.001000");
        seedSpend(team.getId(), "mistral", 200, 80, "0.000500");

        // A row is matched only if EVERY listed column mapped correctly, so ".exists()"
        // on these filters proves the projection round-tripped each field.
        String llamaRow = "$.rows[?(@.teamName == '" + teamName + "' && @.model == 'llama3.1' "
                + "&& @.provider == 'ollama' && @.inputTokens == 100 && @.outputTokens == 50)]";
        String mistralRow = "$.rows[?(@.teamName == '" + teamName + "' && @.model == 'mistral' "
                + "&& @.provider == 'ollama' && @.inputTokens == 200 && @.outputTokens == 80)]";

        webTestClient.get().uri("/admin/spending")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.rows").isArray()
                .jsonPath("$.totalUsd").exists()
                .jsonPath(llamaRow).exists()
                .jsonPath(mistralRow).exists();
    }

    @Test
    void spendingReportIsEmptyForRangeWithNoData() {
        webTestClient.get().uri(b -> b.path("/admin/spending")
                        .queryParam("from", "2020-01-01")
                        .queryParam("to", "2020-01-02")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.rows").isArray()
                .jsonPath("$.rows.length()").isEqualTo(0)
                .jsonPath("$.totalUsd").isEqualTo(0);
    }
}
