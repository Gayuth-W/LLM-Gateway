package com.llmgateway.repository;

import com.llmgateway.model.CircuitBreakerEvent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface CircuitBreakerEventRepository extends ReactiveCrudRepository<CircuitBreakerEvent, Long> {
}
