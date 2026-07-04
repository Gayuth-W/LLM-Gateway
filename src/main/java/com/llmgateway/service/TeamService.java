package com.llmgateway.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.llmgateway.exception.AuthException;
import com.llmgateway.model.Team;
import com.llmgateway.repository.TeamRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Resolves and caches teams by API key. The cache is a Caffeine (Window-TinyLFU)
 * instance with a short write-expiry: it absorbs the per-request auth lookups that
 * would otherwise hammer Postgres on the hot path, while the 10s TTL keeps admin
 * limit/budget changes nearly live. Mutations also invalidate explicitly.
 */
@Service
public class TeamService {

    private final TeamRepository teamRepository;

    private final Cache<String, Team> byApiKey = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(10))
            .maximumSize(10_000)
            .build();

    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    /** Authenticate a bearer key, returning the team or erroring with 401. */
    public Mono<Team> authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new AuthException("Missing API key"));
        }
        Team cached = byApiKey.getIfPresent(apiKey);
        if (cached != null) {
            return Mono.just(cached);
        }
        return teamRepository.findByApiKey(apiKey)
                .doOnNext(team -> byApiKey.put(apiKey, team))
                .switchIfEmpty(Mono.error(new AuthException("Invalid API key")));
    }

    public Mono<Team> byId(Long id) {
        return teamRepository.findById(id);
    }

    /** Drop a single team from the cache (after an admin mutation). */
    public void invalidate(Team team) {
        if (team != null && team.getApiKey() != null) {
            byApiKey.invalidate(team.getApiKey());
        }
    }

    public void invalidateAll() {
        byApiKey.invalidateAll();
    }
}
