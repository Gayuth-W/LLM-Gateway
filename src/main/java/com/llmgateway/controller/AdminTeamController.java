package com.llmgateway.controller;

import com.llmgateway.dto.admin.AlertThresholdRequest;
import com.llmgateway.dto.admin.CreateTeamRequest;
import com.llmgateway.dto.admin.TeamView;
import com.llmgateway.dto.admin.UpdateBudgetRequest;
import com.llmgateway.dto.admin.UpdateLimitsRequest;
import com.llmgateway.model.Team;
import com.llmgateway.repository.TeamRepository;
import com.llmgateway.service.AuditService;
import com.llmgateway.budget.BudgetService;
import com.llmgateway.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Admin API for managing teams: rate limits, budgets, and alert thresholds. Every
 * mutation invalidates the team cache (so changes take effect immediately) and writes
 * an audit record. In production these endpoints sit behind operator authentication.
 */
@RestController
@RequestMapping("/admin/teams")
public class AdminTeamController {

    private final TeamRepository teamRepository;
    private final TeamService teamService;
    private final BudgetService budgetService;
    private final AuditService auditService;

    public AdminTeamController(TeamRepository teamRepository, TeamService teamService,
                              BudgetService budgetService, AuditService auditService) {
        this.teamRepository = teamRepository;
        this.teamService = teamService;
        this.budgetService = budgetService;
        this.auditService = auditService;
    }

    @GetMapping
    public Flux<TeamView> list() {
        return teamRepository.findAll()
                .flatMap(team -> budgetService.spentToday(team.getId())
                        .map(spent -> TeamView.from(team, spent)));
    }

    @GetMapping("/{id}")
    public Mono<TeamView> get(@PathVariable Long id) {
        return teamService.byId(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found")))
                .flatMap(team -> budgetService.spentToday(id).map(spent -> TeamView.from(team, spent)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<TeamView> create(@Valid @RequestBody CreateTeamRequest req,
                                 @RequestHeader(value = "X-Admin-User", defaultValue = "admin") String actor) {
        Team team = new Team();
        team.setApiKey(req.apiKey());
        team.setName(req.name());
        team.setAllowedModels(req.allowedModels() == null ? "" : String.join(",", req.allowedModels()));
        team.setRpmLimit(req.rpmLimit());
        team.setTpmLimit(req.tpmLimit());
        team.setLowPriorityRpm(req.lowPriorityRpm());
        team.setDailyBudgetUsd(req.dailyBudgetUsd() == null ? BigDecimal.ZERO : req.dailyBudgetUsd());
        team.setMonthlyBudgetUsd(req.monthlyBudgetUsd() == null ? BigDecimal.ZERO : req.monthlyBudgetUsd());
        team.setBudgetExhausted(false);
        team.setEnrichmentProfile(req.enrichmentProfile());
        team.setAlertThresholdPct(80);
        OffsetDateTime now = OffsetDateTime.now();
        team.setCreatedAt(now);
        team.setUpdatedAt(now);

        return teamRepository.save(team)
                .doOnNext(saved -> teamService.invalidate(saved))
                .flatMap(saved -> auditService
                        .record(saved.getId(), actor, "create_team", "name=" + saved.getName())
                        .thenReturn(TeamView.from(saved, BigDecimal.ZERO)));
    }

    @PatchMapping("/{id}/limits")
    public Mono<TeamView> updateLimits(@PathVariable Long id,
                                       @Valid @RequestBody UpdateLimitsRequest req,
                                       @RequestHeader(value = "X-Admin-User", defaultValue = "admin") String actor) {
        return mutate(id, actor, "update_limits", team -> {
            if (req.rpmLimit() != null) team.setRpmLimit(req.rpmLimit());
            if (req.tpmLimit() != null) team.setTpmLimit(req.tpmLimit());
            if (req.lowPriorityRpm() != null) team.setLowPriorityRpm(req.lowPriorityRpm());
        });
    }


}
