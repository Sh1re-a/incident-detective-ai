ALTER TABLE runbook_embeddings
    ADD COLUMN provider_transport VARCHAR(32);

UPDATE runbook_embeddings
SET provider_transport = 'developer_api'
WHERE provider_transport IS NULL;

ALTER TABLE runbook_embeddings
    ALTER COLUMN provider_transport SET NOT NULL;

ALTER TABLE runbook_embeddings
    ADD CONSTRAINT runbook_embeddings_provider_transport
        CHECK (provider_transport IN ('developer_api', 'vertex_ai'));

ALTER TABLE runbook_embeddings
    DROP CONSTRAINT runbook_embeddings_identity;

ALTER TABLE runbook_embeddings
    ADD CONSTRAINT runbook_embeddings_identity
        UNIQUE (
            corpus_version,
            evidence_id,
            embedding_model,
            embedding_dimensions,
            embedding_format_version,
            provider_transport
        );

ALTER TABLE runbook_embeddings
    DROP CONSTRAINT runbook_embeddings_chunk_identity;

ALTER TABLE runbook_embeddings
    ADD CONSTRAINT runbook_embeddings_chunk_identity
        UNIQUE (
            corpus_version,
            document_id,
            document_version,
            chunk_id,
            embedding_model,
            embedding_dimensions,
            embedding_format_version,
            provider_transport
        );

DROP INDEX runbook_embeddings_profile_idx;

CREATE INDEX runbook_embeddings_profile_idx
    ON runbook_embeddings (
        corpus_version,
        embedding_model,
        embedding_dimensions,
        embedding_format_version,
        provider_transport
    );
