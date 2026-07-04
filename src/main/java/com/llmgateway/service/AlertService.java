package com.llmgateway.service;

import com.llmgateway.config.GatewayProperties;
import com.llmgateway.model.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Fire-and-forget alerting. If no Slack webhook is configured the alert is just logged,
 * so the gateway runs fully offline with zero external dependencies. Alert delivery never
 * blocks or fails a request: errors are swallowed after logging.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final WebClient slackClient = WebClient.builder().build();
    private final String webhookUrl;

    public AlertService(GatewayProperties props) {
        this.webhookUrl = props.alerts() == null || props.alerts().slack() == null
                ? null : props.alerts().slack().webhookUrl();
    }

    public Mono<Void> budgetWarning(Team team, BigDecimal spent, double fraction) {
        return send(String.format(":warning: Team *%s* at %.0f%% of daily budget ($%.4f spent).",
                team.getName(), fraction * 100, spent));
    }

    public Mono<Void> budgetExhausted(Team team, BigDecimal spent) {
        return send(String.format(":no_entry: Team *%s* has EXHAUSTED its daily budget ($%.4f). Requests now blocked.",
                team.getName(), spent));
    }

    public Mono<Void> circuitBreakerOpened(String model, String fromState) {
        return send(String.format(":electric_plug: Circuit breaker for model *%s* opened (was %s). Traffic shedding.",
                model, fromState));
    }

    public Mono<Void> providerStatusChanged(String model, String from, String to) {
        return send(String.format(":satellite: Model *%s* health changed %s -> %s.", model, from, to));
    }

    private Mono<Void> send(String text) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info("[ALERT] {}", text);
            return Mono.empty();
        }
        return slackClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("text", text))
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(e -> {
                    log.warn("Slack alert delivery failed: {}", e.getMessage());
                    return Mono.empty();
                });
    }
}
