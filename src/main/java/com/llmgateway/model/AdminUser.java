package com.llmgateway.model;

import com.llmgateway.model.enums.Role;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * A human operator of the admin console. Distinct from {@link Team}, which is a
 * machine client of {@code /v1/**}.
 *
 * R2DBC entity: {@code role} is stored as a String rather than an enum, matching
 * the convention in {@link Team}, to avoid needing a custom enum codec on the
 * Postgres driver. Use {@link #roleEnum()} to read it as a {@link Role}.
 */
@Table("admin_users")
public class AdminUser {

    @Id
    private Long id;

    private String username;

    @Column("password_hash")
    private String passwordHash;

    private String role;

    private boolean enabled;

    @Column("created_at")
    private OffsetDateTime createdAt;

    /** The stored role as a typed value. Throws if the column holds an unknown name. */
    public Role roleEnum() {
        return Role.from(role);
    }

    // ---- getters / setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
