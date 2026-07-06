package com.llmgateway.integration;

import com.llmgateway.support.AbstractIntegrationTest;
import com.llmgateway.support.WireMockOllama;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

/**
 * Core proxy behaviour: a successful completion, model authorization, and automatic
 * fallback to the next model in the chain when the primary fails.
 */
class CompletionAndFallbackIntegrationTest extends AbstractIntegrationTest {

    private static final String CHAT = "/v1/chat/completions";

    @Test
    void completesAndAppliesEnterpriseDisclaimer() {
        WireMockOllama.stubSuccess(OLLAMA, "llama3.1", "Hello from llama", 12, 7);

        webTestClient.post().uri(CHAT)
                .header("Authorization", "Bearer sk-enterprise-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"llama3.1","messages":[{"role":"user","content":"Hi there"}],"stream":false}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Provider-Used", "llama3.1")
                .expectHeader().valueEquals("X-Gateway-Fallback", "false")
                .expectBody()
                .jsonPath("$.model").isEqualTo("llama3.1")
                .jsonPath("$.usage.prompt_tokens").isEqualTo(12)
                .jsonPath("$.usage.completion_tokens").isEqualTo(7)
                .jsonPath("$.choices[0].message.content").value(c ->
                        org.assertj.core.api.Assertions.assertThat((String) c).contains("ACME"));
    }

    @Test
    void rejectsModelNotAllowedForTeam() {
        // Free tier may only use llama3.2:1b; llama3.1 must be refused before any provider call.
        webTestClient.post().uri(CHAT)
                .header("Authorization", "Bearer sk-free-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"llama3.1","messages":[{"role":"user","content":"Hi"}],"stream":false}
                        """)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.error").isEqualTo("model_not_allowed");
    }

    @Test
    void fallsBackToNextModelWhenPrimaryFails() {
        WireMockOllama.stubServerError(OLLAMA, "llama3.1");          // primary 503s (and is retried)
        WireMockOllama.stubSuccess(OLLAMA, "mistral", "Served by mistral", 10, 5);

        webTestClient.post().uri(CHAT)
                .header("Authorization", "Bearer sk-enterprise-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"llama3.1","messages":[{"role":"user","content":"Hi"}],"stream":false}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Provider-Used", "mistral")
                .expectHeader().valueEquals("X-Gateway-Fallback", "true")
                .expectBody()
                .jsonPath("$.model").isEqualTo("mistral");
    }

    @Test
    void rejectsMissingApiKey() {
        webTestClient.post().uri(CHAT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"llama3.1","messages":[{"role":"user","content":"Hi"}],"stream":false}
                        """)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
