package com.llmgateway.integration;

import com.llmgateway.support.AbstractIntegrationTest;
import com.llmgateway.support.WireMockOllama;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the streaming (SSE) path passes deltas through and terminates with [DONE]. */
class StreamingIntegrationTest extends AbstractIntegrationTest {

    @Test
    void streamsDeltasAndTerminates() {
        WireMockOllama.stubStream(OLLAMA, "llama3.1",
                new String[]{"Hello", " streamed", " world"}, 9, 6);

        String body = webTestClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer sk-enterprise-key")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue("""
                        {"model":"llama3.1","messages":[{"role":"user","content":"Hi"}],"stream":true}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body).contains("Hello");
        assertThat(body).contains("streamed");
        assertThat(body).contains("chat.completion.chunk");
        assertThat(body).contains("[DONE]");
        // Enterprise disclaimer is appended as a final delta.
        assertThat(body).contains("ACME");
    }
}
