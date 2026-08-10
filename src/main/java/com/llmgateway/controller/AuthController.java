package com.llmgateway.controller;

import com.llmgateway.dto.auth.LoginRequest;
import com.llmgateway.dto.auth.LoginResponse;
import com.llmgateway.dto.auth.MeResponse;
import com.llmgateway.security.AdminUserService;
import com.llmgateway.security.JwtService;
import com.llmgateway.security.SecurityUtils;
import com.llmgateway.service.AuditService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Operator sign-in.
 *
 * <p>{@code POST /auth/login} is the only unauthenticated write endpoint in the
 * gateway. Known gap, called out rather than hidden: it is not rate limited, so
 * it is open to online password guessing. The gateway already runs Bucket4j for
 * team traffic and the honest fix is a per-username and per-IP bucket in front
 * of this handler; it is left out of this change to keep the diff to auth itself.
 *
 * <p>Successful logins are written to {@code audit_logs} with no team attached,
 * so the trail shows who signed in as well as what they changed.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AdminUserService adminUserService;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthController(AdminUserService adminUserService, JwtService jwtService,
                          AuditService auditService) {
        this.adminUserService = adminUserService;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public Mono<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return adminUserService.authenticate(request.username(), request.password())
                .flatMap(user -> auditService
                        .record(null, user.getUsername(), "login", "role=" + user.roleEnum())
                        .thenReturn(user))
                .map(user -> new LoginResponse(
                        jwtService.issue(user),
                        "Bearer",
                        jwtService.ttl().toSeconds(),
                        user.getUsername(),
                        user.roleEnum().name()));
    }

    /**
     * Echo back the identity inside the presented token. The console calls this on
     * load to decide whether a stored token is still good and which controls to
     * render; it doubles as a cheap token-validity probe.
     */
    @GetMapping("/me")
    public Mono<MeResponse> me() {
        return SecurityUtils.currentActor()
                .map(actor -> new MeResponse(actor.username(), actor.role().name()));
    }
}
