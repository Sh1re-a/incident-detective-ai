ALTER TABLE runbook_embeddings
    RENAME COLUMN billable_characters TO input_characters;

ALTER TABLE runbook_embeddings
    ADD COLUMN provider_billable_characters INTEGER
        CHECK (provider_billable_characters >= 0);

ALTER TABLE runbook_embeddings
    ALTER COLUMN input_tokens DROP NOT NULL;

ALTER TABLE runbook_embeddings
    RENAME COLUMN input_tokens TO provider_input_tokens;
