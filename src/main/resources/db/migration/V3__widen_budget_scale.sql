-- Budgets are enforced against costs tracked to 6 decimal places
-- (spending_records.cost_usd is NUMERIC(14,6)), but the budget columns were
-- NUMERIC(12,4). That rounded any sub-$0.0001 budget down to 0.0000 on insert,
-- and a zero budget is treated as "no limit", so fine-grained caps silently
-- never enforced. Widen the budget columns to match the cost scale.
ALTER TABLE teams ALTER COLUMN daily_budget_usd   TYPE NUMERIC(14,6);
ALTER TABLE teams ALTER COLUMN monthly_budget_usd TYPE NUMERIC(14,6);
