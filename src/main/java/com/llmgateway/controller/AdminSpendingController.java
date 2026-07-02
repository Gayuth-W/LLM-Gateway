package com.llmgateway.controller;

import com.llmgateway.dto.admin.SpendingReport;
import com.llmgateway.dto.admin.TeamSpendingRow;
import com.llmgateway.repository.SpendingRecordRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Spending dashboard data: per-team/model rollups over a date range, plus the range
 * total. Backed by the durable spending_records table (the async-flushed rollup).
 */
@RestController
@RequestMapping("/admin/spending")
public class AdminSpendingController {

    private final SpendingRecordRepository spendingRepository;

    public AdminSpendingController(SpendingRecordRepository spendingRepository) {
        this.spendingRepository = spendingRepository;
    }

    @GetMapping
    public Mono<SpendingReport> report(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate begin = from != null ? from : end.minusDays(7);

        return spendingRepository.spendingBetween(begin, end)
                .collectList()
                .map(rows -> new SpendingReport(begin, end, totalCost(rows), rows));
    }

    private BigDecimal totalCost(List<TeamSpendingRow> rows) {
        return rows.stream()
                .map(TeamSpendingRow::costUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
