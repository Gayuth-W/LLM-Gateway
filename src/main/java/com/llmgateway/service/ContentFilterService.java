package com.llmgateway.service;

import com.llmgateway.dto.ChatMessage;
import com.llmgateway.dto.LLMRequest;
import com.llmgateway.exception.ContentPolicyViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;

/**
 * Minimal request-side content gate, wired in as an explicit pipeline stage. Today it
 * rejects empty prompts and an (empty by default) denylist; in production this is where
 * a moderation model or policy service would sit. Violations are NON-retryable.
 */
@Service
public class ContentFilterService {

    // Intentionally empty by default; populated from config/policy in a real deployment.
    private static final List<String> DENYLIST = List.of();

    public Mono<LLMRequest> screen(LLMRequest request) {
        boolean hasContent = request.messages().stream()
                .map(ChatMessage::content)
                .anyMatch(c -> c != null && !c.isBlank());
        if (!hasContent) {
            return Mono.error(new ContentPolicyViolationException("Prompt contains no content"));
        }
        String joined = request.messages().stream()
                .map(m -> m.content() == null ? "" : m.content().toLowerCase(Locale.ROOT))
                .reduce("", (a, b) -> a + " " + b);
        for (String banned : DENYLIST) {
            if (joined.contains(banned)) {
                return Mono.error(new ContentPolicyViolationException("Prompt violates content policy"));
            }
        }
        return Mono.just(request);
    }
}
