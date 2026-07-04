package com.llmgateway.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SpendingReport(
        LocalDate from,
        LocalDate to,
        BigDecimal totalUsd,
        List<TeamSpendingRow> rows
) {}
