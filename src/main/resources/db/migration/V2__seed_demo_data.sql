-- =====================================================================
-- Demo teams so `docker-compose up` immediately shows a working system.
-- These API keys are intentionally well-known for local testing.
-- =====================================================================

-- 1) Enterprise team: generous limits, compliance enrichment, real budget.
INSERT INTO teams (api_key, name, allowed_models, rpm_limit, tpm_limit, low_priority_rpm,
                   daily_budget_usd, monthly_budget_usd, enrichment_profile, slack_channel)
VALUES ('sk-enterprise-key', 'ACME Enterprise', 'llama3.1,gemma3,gemma3:1b',
        120, 120000, 40, 25.0000, 500.0000, 'enterprise', '#acme-llm-alerts');

-- 2) Internal team: medium limits, no enrichment.
INSERT INTO teams (api_key, name, allowed_models, rpm_limit, tpm_limit, low_priority_rpm,
                   daily_budget_usd, monthly_budget_usd, enrichment_profile)
VALUES ('sk-internal-key', 'Internal Tools', 'llama3.1,gemma3:1b',
        60, 60000, 20, 5.0000, 100.0000, 'internal');

-- 3) Free-tier team: tight limits + tiny budget, so 429s and 402s are easy to trigger in a demo.
INSERT INTO teams (api_key, name, allowed_models, rpm_limit, tpm_limit, low_priority_rpm,
                   daily_budget_usd, monthly_budget_usd, enrichment_profile)
VALUES ('sk-free-key', 'Free Tier', 'gemma3:1b',
        5, 2000, 2, 0.0100, 0.1000, 'internal');
