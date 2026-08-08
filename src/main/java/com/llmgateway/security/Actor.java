package com.llmgateway.security;

import com.llmgateway.model.enums.Role;

/**
 * The verified identity behind an admin request: who they are and what they may do.
 * This is what lands in {@code audit_logs.actor} -- it comes from a signed token,
 * never from a client-supplied header.
 */
public record Actor(String username, Role role) {
}
