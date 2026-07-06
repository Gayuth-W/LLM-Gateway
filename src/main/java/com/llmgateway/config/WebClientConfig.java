package com.llmgateway.config;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.micrometer.observation.ObservationRegistry;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import reactor.netty.http.client.HttpClient;

/**
 * Builds the dedicated {@link WebClient} used to reach Ollama. Connect timeout is
 * short (fail fast on a dead endpoint); the per-request read budget is enforced in
 * {@code OllamaProvider} via {@code .timeout(...)} because first-token latency on CPU
 * can be long and is policy, not transport.
 *
 * The ObservationRegistry is attached so each outbound call to Ollama is recorded as a
 * client observation — which, via the tracing bridge, becomes a child span under the
 * inbound request. Without it the WebClient calls happen outside the trace and each
 * request shows only a single span.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient ollamaWebClient(GatewayProperties props, ObservationRegistry observationRegistry) {
        long connectMillis = props.ollama().connectTimeout().toMillis();
        long readSeconds = Math.max(1, props.ollama().requestTimeout().toSeconds());

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectMillis)
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(readSeconds, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(props.ollama().baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // Allow large non-streaming completions to buffer (16 MB).
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .observationRegistry(observationRegistry)
                .build();
    }
}