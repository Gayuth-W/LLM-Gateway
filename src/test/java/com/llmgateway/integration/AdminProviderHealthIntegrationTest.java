package com.llmgateway.integration;

import com.llmgateway.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * GET /admin/providers/health returns a health row for every configured model
 * (status defaults to HEALTHY until the background checker has samples).
 */
class AdminProviderHealthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void healthSnapshotListsAllConfiguredModels() {
        webTestClient.get().uri("/admin/providers/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                // three models are configured in application-test.yml
                .jsonPath("$.length()").isEqualTo(3)
                .jsonPath("$[0].model").exists()
                .jsonPath("$[0].status").exists()
                .jsonPath("$[0].sampleCount").exists();
    }
}
