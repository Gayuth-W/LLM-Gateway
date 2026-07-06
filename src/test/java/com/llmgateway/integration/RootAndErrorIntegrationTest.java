package com.llmgateway.integration;

import com.llmgateway.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * The public root index and the global error handler's treatment of unmapped routes,
 * which previously surfaced as 500 instead of a clean 404.
 */
class RootAndErrorIntegrationTest extends AbstractIntegrationTest {

    @Test
    void rootReturnsServiceIndex() {
        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("llm-gateway")
                .jsonPath("$.status").isEqualTo("ok")
                .jsonPath("$.endpoints.chat").exists();
    }

    @Test
    void unmappedPathReturnsCleanNotFound() {
        webTestClient.get().uri("/does-not-exist")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error").isEqualTo("not_found");
    }
}
