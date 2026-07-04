package com.llmgateway.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiny landing endpoint so hitting the gateway in a browser at "/" returns a useful
 * index instead of a 404. Not under /v1, so it needs no API key.
 */
@RestController
public class RootController {

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> index() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("chat", "POST /v1/chat/completions  (header: Authorization: Bearer <team-key>)");
        endpoints.put("health", "GET /actuator/health");
        endpoints.put("metrics", "GET /actuator/prometheus");
        endpoints.put("adminTeams", "GET /admin/teams");
        endpoints.put("adminProviderHealth", "GET /admin/providers/health");
        endpoints.put("adminSpending", "GET /admin/spending?from=YYYY-MM-DD&to=YYYY-MM-DD");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("service", "llm-gateway");
        root.put("status", "ok");
        root.put("version", "1.0.0");
        root.put("endpoints", endpoints);
        return root;
    }
}
