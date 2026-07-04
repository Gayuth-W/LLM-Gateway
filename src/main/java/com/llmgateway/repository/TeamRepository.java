package com.llmgateway.repository;

import com.llmgateway.model.Team;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface TeamRepository extends ReactiveCrudRepository<Team, Long> {

    Mono<Team> findByApiKey(String apiKey);

    @Modifying
    @Query("UPDATE teams SET budget_exhausted = :exhausted, updated_at = now() WHERE id = :id")
    Mono<Integer> updateBudgetExhausted(@Param("id") Long id, @Param("exhausted") boolean exhausted);
}
