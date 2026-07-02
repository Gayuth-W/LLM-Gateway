package com.llmgateway.budget;

import com.llmgateway.config.GatewayProperties;
import com.llmgateway.model.Team;
import com.llmgateway.repository.SpendingRecordRepository;
import com.llmgateway.repository.TeamRepository;
import com.llmgateway.service.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Two-layer budget tracking:
 *
 *   1. LIVE ENFORCEMENT (Redis): every request INCRBYFLOATs a per-team/day USD
 *      counter. This is the source of truth for "are we over budget right now" and
 *      is correct across instances. Threshold crossings (warn %, 100%) fire exactly
 *      once via Redis SETNX guards.
 *
 *   2. DURABLE ROLLUP (Postgres): per-request deltas are pushed onto an in-memory,
 *      lock-free queue (O(1), no DB write on the hot path). A scheduled flush drains
 *      and aggregates the queue, then UPSERTs into spending_records. Worst-case loss
 *      on crash is one flush interval of reporting data; live enforcement is unaffected.
 */
@Service
public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);

    private final ReactiveStringRedisTemplate redis;
    private final SpendingRecordRepository spendingRepo;
    private final TeamRepository teamRepo;
    private final AlertService alertService;
    private final GatewayProperties props;


    public BudgetService(ReactiveStringRedisTemplate redis,
                         SpendingRecordRepository spendingRepo,
                         TeamRepository teamRepo,
                         AlertService alertService,
                         GatewayProperties props) {
        this.redis = redis;
        this.spendingRepo = spendingRepo;
        this.teamRepo = teamRepo;
        this.alertService = alertService;
        this.props = props;
    }

    //Generates Redis key for total daily spending
    private String aggKey(Long teamId, LocalDate day) {
        return "budget:" + teamId + ":" + day;
    }


    //Generates special Redis keys.
    private String key(Long teamId, LocalDate day, String suffix) {
        return "budget:" + teamId + ":" + day + ":" + suffix;
    }
}
