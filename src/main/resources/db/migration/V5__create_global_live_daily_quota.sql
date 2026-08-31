CREATE TABLE global_live_daily_quota (
    quota_day DATE PRIMARY KEY,
    consumed_starts INTEGER NOT NULL DEFAULT 0
        CHECK (consumed_starts >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
