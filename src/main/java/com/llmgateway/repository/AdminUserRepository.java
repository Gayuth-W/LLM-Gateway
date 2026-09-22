package com.llmgateway.repository;

import com.llmgateway.model.AdminUser;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface AdminUserRepository extends ReactiveCrudRepository<AdminUser, Long> {

    Mono<AdminUser> findByUsername(String username);
}
