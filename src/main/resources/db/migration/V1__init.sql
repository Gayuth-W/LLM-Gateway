-- =====================================================================
-- Core schema for the LLM Gateway.
-- =====================================================================

CREATE TABLE teams (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    api_key             VARCHAR(128) NOT NULL UNIQUE,
    name                VARCHAR(128) NOT NULL,
    allowed_models      TEXT         NOT NULL DEFAULT '',   -- comma-separated model names
    rpm_limit           INT          NOT NULL DEFAULT 60,   -- requests / minute (main bucket)
    tpm_limit           INT          NOT NULL DEFAULT 60000,-- tokens   / minute
    low_priority_rpm    INT          NOT NULL DEFAULT 20,   -- reserved bucket for low-priority work
    daily_budget_usd    NUMERIC(12,4) NOT NULL DEFAULT 5.0000,
    monthly_budget_usd  NUMERIC(12,4) NOT NULL DEFAULT 100.0000,
    budget_exhausted    BOOLEAN      NOT NULL DEFAULT FALSE,
    enrichment_profile  VARCHAR(32),                        -- 'enterprise' | 'internal' | NULL
    alert_threshold_pct INT          NOT NULL DEFAULT 80,
    slack_channel       VARCHAR(128),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Daily spend rollup per team/model. Redis holds the hot counter; this is the
-- durable record flushed periodically (UPSERT keyed by team/day/model).
CREATE TABLE spending_records (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    team_id       BIGINT       NOT NULL REFERENCES teams(id),
    day           DATE         NOT NULL,
    model         VARCHAR(64)  NOT NULL,
    provider      VARCHAR(64)  NOT NULL,
    input_tokens  BIGINT       NOT NULL DEFAULT 0,
    output_tokens BIGINT       NOT NULL DEFAULT 0,
    cost_usd      NUMERIC(14,6) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_spend UNIQUE (team_id, day, model)
);
CREATE INDEX idx_spend_team_day ON spending_records (team_id, day);

-- Provider (model) health state transitions, for post-incident analysis.
CREATE TABLE provider_health_events (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    model           VARCHAR(64)  NOT NULL,
    previous_status VARCHAR(16)  NOT NULL,
    new_status      VARCHAR(16)  NOT NULL,
    error_rate      DOUBLE PRECISION NOT NULL,
    p99_latency_ms  DOUBLE PRECISION NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_health_model_time ON provider_health_events (model, created_at);

-- Circuit breaker state transitions.
CREATE TABLE circuit_breaker_events (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider   VARCHAR(64) NOT NULL,            -- the model the breaker guards
    from_state VARCHAR(24) NOT NULL,
    to_state   VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cb_provider_time ON circuit_breaker_events (provider, created_at);

-- Every time the router serves a different model than requested.
CREATE TABLE fallback_events (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    team_id         BIGINT      REFERENCES teams(id),
    requested_model VARCHAR(64) NOT NULL,
    served_model    VARCHAR(64) NOT NULL,
    reason          VARCHAR(256) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_fallback_time ON fallback_events (created_at);

-- Admin mutation audit trail.
CREATE TABLE audit_logs (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    team_id    BIGINT      REFERENCES teams(id),
    actor      VARCHAR(128) NOT NULL,
    action     VARCHAR(128) NOT NULL,
    details    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_time ON audit_logs (created_at);
