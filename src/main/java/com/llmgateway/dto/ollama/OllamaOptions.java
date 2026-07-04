package com.llmgateway.dto.ollama;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Subset of Ollama generation options the gateway forwards. Nulls are omitted. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaOptions(
        Double temperature,
        @JsonProperty("num_predict") Integer numPredict
) {}
