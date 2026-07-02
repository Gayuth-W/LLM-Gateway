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


}
