package com.llmgateway.provider;

import com.llmgateway.config.GatewayProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps a model name to the provider that serves it. With a single Ollama backend
 * every known model resolves to the one {@link OllamaProvider}; the indirection is
 * what lets a hosted provider be slotted in per-model later without touching callers.
 *
 * Backed by a {@link ConcurrentHashMap} for O(1), lock-free lookups on the hot path.
 */
@Component
public class ProviderRegistry {

    private final Map<String, LLMProvider> byModel = new ConcurrentHashMap<>();
    private final List<String> knownModels;

    public ProviderRegistry(List<LLMProvider> providers, GatewayProperties props) {
        // Single provider today; register every configured model against it.
        LLMProvider primary = providers.stream()
                .filter(p -> "ollama".equals(p.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No Ollama provider on the classpath"));
        this.knownModels = List.copyOf(props.modelNames());
        for (String model : knownModels) {
            byModel.put(model, primary);
        }
    }

    /** O(1) lookup. Returns null if the model is unknown to the gateway. */
    public LLMProvider forModel(String model) {
        return byModel.get(model);
    }

    public boolean isKnown(String model) {
        return byModel.containsKey(model);
    }

    public List<String> knownModels() {
        return knownModels;
    }
}
