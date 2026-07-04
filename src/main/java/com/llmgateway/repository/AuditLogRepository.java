package com.llmgateway.repository;

import com.llmgateway.model.AuditLog;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface AuditLogRepository extends ReactiveCrudRepository<AuditLog, Long> {
}
