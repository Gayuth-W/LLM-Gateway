package com.llmgateway.support;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Helpers to stub Ollama's POST /api/chat per model, matched on the request body's
 * "model" field so a single endpoint can simulate different models behaving differently.
 */
public final class WireMockOllama {

    private WireMockOllama() {}

    /** Non-streaming success for a specific model. */
    public static void stubSuccess(WireMockServer server, String model, String content,
                                   int promptTokens, int evalTokens) {
        server.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(matchingJsonPath("$.model", equalTo(model)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonBody(model, content, promptTokens, evalTokens))));
    }

    /** Server error (retryable) for a specific model. */
    public static void stubServerError(WireMockServer server, String model) {
        server.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(matchingJsonPath("$.model", equalTo(model)))
                .willReturn(aResponse().withStatus(503).withBody("upstream down")));
    }

    /** Streaming (NDJSON) success: a few deltas then a terminal line with token counts. */
    public static void stubStream(WireMockServer server, String model, String[] deltas,
                                  int promptTokens, int evalTokens) {
        StringBuilder ndjson = new StringBuilder();
        for (String delta : deltas) {
            ndjson.append(streamLine(model, delta, false, null, null)).append('\n');
        }
        ndjson.append(streamLine(model, "", true, promptTokens, evalTokens)).append('\n');

        server.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(matchingJsonPath("$.model", equalTo(model)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-ndjson")
                        .withBody(ndjson.toString())));
    }

    /** Default catch-all success so requests for any other model still resolve. */
    public static void stubDefaultSuccess(WireMockServer server) {
        server.stubFor(post(urlEqualTo("/api/chat"))
                .atPriority(10) // lower priority than model-specific stubs
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonBody("llama3.1", "ok", 5, 3))));
    }

    private static String jsonBody(String model, String content, int prompt, int eval) {
        return """
            {"model":"%s","message":{"role":"assistant","content":"%s"},"done":true,"prompt_eval_count":%d,"eval_count":%d}
            """.formatted(model, content, prompt, eval);
    }

    private static String streamLine(String model, String content, boolean done,
                                     Integer prompt, Integer eval) {
        if (done) {
            return """
                {"model":"%s","message":{"role":"assistant","content":""},"done":true,"prompt_eval_count":%d,"eval_count":%d}
                """.formatted(model, prompt, eval).strip();
        }
        return """
            {"model":"%s","message":{"role":"assistant","content":"%s"},"done":false}
            """.formatted(model, content).strip();
    }
}
