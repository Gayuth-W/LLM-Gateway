package com.llmgateway.controller;

import com.llmgateway.dto.admin.AlertThresholdRequest;
import com.llmgateway.dto.admin.CreateTeamRequest;
import com.llmgateway.dto.admin.TeamView;
import com.llmgateway.dto.admin.UpdateBudgetRequest;
import com.llmgateway.dto.admin.UpdateLimitsRequest;
import com.llmgateway.model.Team;
import com.llmgateway.repository.TeamRepository;
import com.llmgateway.security.SecurityUtils;
import com.llmgateway.service.AuditService;
import com.llmgateway.budget.BudgetService;
import com.llmgateway.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Admin API for managing teams: rate limits, budgets, and alert thresholds. Every
 * mutation invalidates the team cache (so changes take effect immediately) and writes
 * an audit record.
 *
 * <p><b>Authorization.</b> Roles are assigned per handler by blast radius, not per
 * controller:
 * <ul>
 *   <li>reads are VIEWER;</li>
 *   <li>the PATCH handlers are OPERATOR -- they can cause a cost spike or an outage,
 *       but cannot create a principal;</li>
 *   <li>{@code POST /admin/teams} is ADMIN, because it mints a team API key. Issuing
 *       a credential is a categorically larger privilege than tuning a number.</li>
 * </ul>
 * ADMIN and OPERATOR satisfy the lower checks through the role hierarchy.
 *
 * <p><b>Audit actor.</b> The actor recorded against every mutation is read from the
 * verified JWT subject. It used to come from an {@code X-Admin-User} request header,
 * which any caller could set to any value -- meaning the audit trail could name
 * someone who had done nothing. Only the token can speak for the operator now.
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
    @PreAuthorize("hasRole('VIEWER')")
    public Flux<TeamView> list() {
        return teamRepository.findAll()
                .flatMap(team -> budgetService.spentToday(team.getId())
                        .map(spent -> TeamView.from(team, spent)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VIEWER')")
    public Mono<TeamView> get(@PathVariable Long id) {
        return teamService.byId(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found")))
                .flatMap(team -> budgetService.spentToday(id).map(spent -> TeamView.from(team, spent)));
    }

    /** Issues a new team API key, so this is the one handler restricted to ADMIN. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<TeamView> create(@Valid @RequestBody CreateTeamRequest req) {
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

        return SecurityUtils.currentUsername().flatMap(actor -> teamRepository.save(team)
                .doOnNext(saved -> teamService.invalidate(saved))
                .flatMap(saved -> auditService
                        .record(saved.getId(), actor, "create_team", "name=" + saved.getName())
                        .thenReturn(TeamView.from(saved, BigDecimal.ZERO))));
    }

    @PatchMapping("/{id}/limits")
    @PreAuthorize("hasRole('OPERATOR')")
    public Mono<TeamView> updateLimits(@PathVariable Long id,
                                       @Valid @RequestBody UpdateLimitsRequest req) {
        return mutate(id, "update_limits", team -> {
            if (req.rpmLimit() != null) team.setRpmLimit(req.rpmLimit());
            if (req.tpmLimit() != null) team.setTpmLimit(req.tpmLimit());
            if (req.lowPriorityRpm() != null) team.setLowPriorityRpm(req.lowPriorityRpm());
        });
    }

    @PatchMapping("/{id}/budget")
    @PreAuthorize("hasRole('OPERATOR')")
    public Mono<TeamView> updateBudget(@PathVariable Long id,
                                       @Valid @RequestBody UpdateBudgetRequest req) {
        return mutate(id, "update_budget", team -> {
            if (req.dailyBudgetUsd() != null) team.setDailyBudgetUsd(req.dailyBudgetUsd());
            if (req.monthlyBudgetUsd() != null) team.setMonthlyBudgetUsd(req.monthlyBudgetUsd());
            // Raising the budget clears a prior exhaustion flag.
            team.setBudgetExhausted(false);
        });
    }

    @PatchMapping("/{id}/alert-threshold")
    @PreAuthorize("hasRole('OPERATOR')")
    public Mono<TeamView> updateAlertThreshold(@PathVariable Long id,
                                               @Valid @RequestBody AlertThresholdRequest req) {
        return mutate(id, "update_alert_threshold", team -> {
            team.setAlertThresholdPct(req.alertThresholdPct());
            if (req.slackChannel() != null) team.setSlackChannel(req.slackChannel());
        });
    }

    /** Shared load -> apply -> persist -> invalidate -> audit -> view flow. */
    private Mono<TeamView> mutate(Long id, String action, java.util.function.Consumer<Team> change) {
        return SecurityUtils.currentUsername().flatMap(actor -> teamService.byId(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found")))
                .flatMap(team -> {
                    change.accept(team);
                    team.setUpdatedAt(OffsetDateTime.now());
                    return teamRepository.save(team);
                })
                .doOnNext(teamService::invalidate)
                .flatMap(saved -> auditService.record(saved.getId(), actor, action, "")
                        .then(budgetService.spentToday(saved.getId()))
                        .map(spent -> TeamView.from(saved, spent))));
    }
}
