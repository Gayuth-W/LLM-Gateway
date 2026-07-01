package com.llmgateway.filter;

import com.llmgateway.exception.AuthException;
import com.llmgateway.exception.BudgetExhaustedException;
import com.llmgateway.model.enums.RequestPriority;
import com.llmgateway.service.TeamService;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Authenticates inbound API traffic. Runs only for the public {@code /v1/**} surface;
 * admin endpoints are expected to sit behind separate operator auth.
 *
 * Responsibilities (cheap, no request-body access):
 *   - resolve the Bearer key to a Team (401 on miss);
 *   - fast-reject teams already flagged budget-exhausted (402);
 *   - stash Team + request priority on the exchange for the controller.
 *
 * Token-aware checks (rate limiting, live budget) run at the controller boundary,
 * where the parsed body and token estimate are available.
 */
@Component
@Order(1)
public class TeamAuthFilter implements WebFilter {

    private final TeamService teamService;

    public TeamAuthFilter(TeamService teamService) {
        this.teamService = teamService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/v1/")) {
            return chain.filter(exchange);
        }

        String apiKey = extractBearer(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        RequestPriority priority = RequestPriority.fromHeader(
                exchange.getRequest().getHeaders().getFirst("X-Priority"));

        return teamService.authenticate(apiKey)
                .flatMap(team -> {
                    if (team.isBudgetExhausted()) {
                        return Mono.error(new BudgetExhaustedException(
                                "Daily budget exhausted for team " + team.getName()));
                    }
                    exchange.getAttributes().put(RequestContext.TEAM, team);
                    exchange.getAttributes().put(RequestContext.PRIORITY, priority);
                    return chain.filter(exchange);
                });
    }

    private String extractBearer(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null; // authenticate(null) -> 401
        }
        return header.substring(7).trim();
    }
}
