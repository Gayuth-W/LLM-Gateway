package com.llmgateway.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiny landing endpoint so hitting the gateway in a browser at "/" returns a useful
 * index instead of a 404. Public: not under /v1 so it needs no team API key, and
 * explicitly permitted in the security chain so it needs no operator token either.
 */
@RestController
public class RootController {

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> index() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("chat", "POST /v1/chat/completions  (header: Authorization: Bearer <team-key>)");
        endpoints.put("health", "GET /actuator/health");
        endpoints.put("metrics", "GET /actuator/prometheus");
        endpoints.put("login", "POST /auth/login  {\"username\",\"password\"} -> operator JWT");
        endpoints.put("whoami", "GET /auth/me  (header: Authorization: Bearer <jwt>)");
        endpoints.put("adminTeams", "GET /admin/teams");
        endpoints.put("adminProviderHealth", "GET /admin/providers/health");
        endpoints.put("adminSpending", "GET /admin/spending?from=YYYY-MM-DD&to=YYYY-MM-DD");
        endpoints.put("adminAuth", "All /admin/** endpoints require an operator JWT (roles: VIEWER < OPERATOR < ADMIN)");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("service", "llm-gateway");
        root.put("status", "ok");
        root.put("version", "1.0.0");
        root.put("endpoints", endpoints);
        return root;
    }
}
