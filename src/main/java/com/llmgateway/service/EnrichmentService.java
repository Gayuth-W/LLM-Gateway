package com.llmgateway.service;

import com.llmgateway.config.GatewayProperties;
import com.llmgateway.dto.ChatMessage;
import com.llmgateway.dto.LLMRequest;
import com.llmgateway.model.Team;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a team's enrichment profile:
 *   - a system prefix is injected as/merged into the leading system message before forwarding;
 *   - a disclaimer suffix is appended to the model's final text on the way out.
 *
 * Profiles are config-driven (gateway.enrichment.profiles) so policy changes need no redeploy.
 */
@Service
public class EnrichmentService {

    private final GatewayProperties props;

    public EnrichmentService(GatewayProperties props) {
        this.props = props;
    }

    private GatewayProperties.Enrichment.Profile profileFor(Team team) {
        if (props.enrichment() == null || team.getEnrichmentProfile() == null) {
            return null;
        }
        return props.enrichment().profile(team.getEnrichmentProfile());
    }

    /** Returns a request with the team's system prefix applied (or the original if none). */
    public LLMRequest enrichRequest(Team team, LLMRequest request) {
        GatewayProperties.Enrichment.Profile profile = profileFor(team);
        if (profile == null || profile.systemPrefix() == null || profile.systemPrefix().isBlank()) {
            return request;
        }
        String prefix = profile.systemPrefix();
        List<ChatMessage> messages = new ArrayList<>(request.messages());

        if (!messages.isEmpty() && "system".equals(messages.get(0).role())) {
            ChatMessage existing = messages.get(0);
            messages.set(0, new ChatMessage("system", prefix + "\n\n" + existing.content()));
        } else {
            messages.add(0, ChatMessage.system(prefix));
        }
        return request.withMessages(messages);
    }
}
