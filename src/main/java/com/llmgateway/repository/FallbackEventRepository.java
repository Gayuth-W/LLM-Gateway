package com.llmgateway.repository;

import com.llmgateway.model.FallbackEvent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface FallbackEventRepository extends ReactiveCrudRepository<FallbackEvent, Long> {
}
