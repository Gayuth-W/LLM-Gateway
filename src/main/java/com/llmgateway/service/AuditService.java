package com.llmgateway.service;

import com.llmgateway.model.AuditLog;
import com.llmgateway.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Records admin mutations to the audit_logs table. Failures are logged, never fatal. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public Mono<Void> record(Long teamId, String actor, String action, String details) {
        return repository.save(new AuditLog(teamId, actor, action, details))
                .doOnError(e -> log.warn("Audit write failed for action {}", action, e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}
