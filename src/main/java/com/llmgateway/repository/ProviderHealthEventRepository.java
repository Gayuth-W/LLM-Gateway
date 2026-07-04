package com.llmgateway.repository;

import com.llmgateway.model.ProviderHealthEvent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface ProviderHealthEventRepository extends ReactiveCrudRepository<ProviderHealthEvent, Long> {
}
