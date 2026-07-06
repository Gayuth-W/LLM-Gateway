package com.llmgateway.gatling;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Load test for the gateway, run via the `load-test` Maven profile:
 *
 *   mvn -Pload-test gatling:test -Dgatling.simulationClass=com.llmgateway.gatling.GatewaySimulation
 *
 * Point it at a running gateway with -Dgateway.baseUrl=http://localhost:8080 (default).
 * Under load you should see a mix of 200s and 429s as buckets engage; the report shows
 * the gateway's own added latency (Ollama dominates absolute numbers).
 */
public class GatewaySimulation extends Simulation {

    private final String baseUrl = System.getProperty("gateway.baseUrl", "http://localhost:8080");
    private final String apiKey = System.getProperty("gateway.apiKey", "sk-enterprise-key");
    private final int users = Integer.getInteger("gateway.users", 50);
    private final int rampSeconds = Integer.getInteger("gateway.ramp", 30);

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(baseUrl)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            .header("Authorization", "Bearer " + apiKey)
            .userAgentHeader("gatling-gateway-loadtest");

    private final ScenarioBuilder chat = scenario("Chat completions under load")
            .exec(http("POST /v1/chat/completions")
                    .post("/v1/chat/completions")
                    .body(StringBody("""
                            {"model":"llama3.1","messages":[{"role":"user","content":"Summarise the CAP theorem in one sentence."}],"stream":false,"max_tokens":64}
                            """))
                    // The gateway should always answer with a defined outcome, never a 5xx storm.
                    .check(status().in(200, 402, 429)))
            .pause(Duration.ofMillis(200));

    {
        setUp(
                chat.injectOpen(rampUsers(users).during(Duration.ofSeconds(rampSeconds)))
        ).protocols(httpProtocol)
         .assertions(
                 global().responseTime().max().lt(120_000),
                 global().failedRequests().percent().lt(1.0)
         );
    }
}
