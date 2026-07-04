package com.llmgateway.controller;

import com.llmgateway.dto.admin.ProviderHealthView;
import com.llmgateway.resilience.ProviderHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** Admin visibility into per-model health (status, error rate, p99 latency, samples). */
@RestController
@RequestMapping("/admin/providers")
public class AdminProviderController {

    private final ProviderHealthService healthService;

    public AdminProviderController(ProviderHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public Flux<ProviderHealthView> health() {
        return healthService.snapshot();
    }
}
