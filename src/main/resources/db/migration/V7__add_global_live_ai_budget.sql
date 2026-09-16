ALTER TABLE global_live_daily_quota
    ADD COLUMN consumed_micro_usd BIGINT NOT NULL DEFAULT 0
        CHECK (consumed_micro_usd >= 0);

-- V5 counted starts but not money. Treat every existing start as the largest
-- public operation allowance so an in-day upgrade cannot reopen spent budget.
UPDATE global_live_daily_quota
SET consumed_micro_usd = consumed_starts::BIGINT * 25000;
