package com.llmgateway.repository;

import com.llmgateway.dto.admin.TeamSpendingRow;
import com.llmgateway.model.SpendingRecord;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface SpendingRecordRepository extends ReactiveCrudRepository<SpendingRecord, Long> {

    /**
     * Atomic UPSERT of a team/day/model spend bucket. Token and cost deltas are
     * added on conflict, so concurrent flushes never lose writes.
     */
    @Modifying
    @Query("""
        INSERT INTO spending_records (team_id, day, model, provider, input_tokens, output_tokens, cost_usd, updated_at)
        VALUES (:teamId, :day, :model, :provider, :inputTokens, :outputTokens, :costUsd, now())
        ON CONFLICT (team_id, day, model) DO UPDATE SET
            input_tokens  = spending_records.input_tokens  + EXCLUDED.input_tokens,
            output_tokens = spending_records.output_tokens + EXCLUDED.output_tokens,
            cost_usd      = spending_records.cost_usd       + EXCLUDED.cost_usd,
            updated_at    = now()
        """)
    Mono<Integer> upsertSpend(@Param("teamId") Long teamId,
                              @Param("day") LocalDate day,
                              @Param("model") String model,
                              @Param("provider") String provider,
                              @Param("inputTokens") long inputTokens,
                              @Param("outputTokens") long outputTokens,
                              @Param("costUsd") BigDecimal costUsd);

    @Query("""
        SELECT s.team_id       AS team_id,
               t.name          AS team_name,
               s.model         AS model,
               s.provider      AS provider,
               s.input_tokens  AS input_tokens,
               s.output_tokens AS output_tokens,
               s.cost_usd      AS cost_usd
        FROM spending_records s JOIN teams t ON t.id = s.team_id
        WHERE s.day BETWEEN :from AND :to
        ORDER BY s.cost_usd DESC
        """)
    Flux<TeamSpendingRow> spendingBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
