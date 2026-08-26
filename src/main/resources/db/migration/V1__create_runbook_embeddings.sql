CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE runbook_embeddings (
    id BIGSERIAL PRIMARY KEY,
    corpus_version VARCHAR(80) NOT NULL,
    evidence_id VARCHAR(160) NOT NULL,
    document_id VARCHAR(160) NOT NULL,
    document_version VARCHAR(80) NOT NULL,
    chunk_id VARCHAR(160) NOT NULL,
    title VARCHAR(240) NOT NULL,
    display_summary VARCHAR(500) NOT NULL,
    source_ref VARCHAR(320) NOT NULL,
    body TEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    embedding_model VARCHAR(160) NOT NULL,
    embedding_dimensions INTEGER NOT NULL CHECK (embedding_dimensions = 768),
    embedding_format_version VARCHAR(80) NOT NULL,
    embedding vector(768) NOT NULL,
    billable_characters INTEGER NOT NULL CHECK (billable_characters >= 0),
    input_tokens DOUBLE PRECISION NOT NULL CHECK (input_tokens >= 0),
    embedding_latency_ms BIGINT NOT NULL CHECK (embedding_latency_ms >= 0),
    embedded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT runbook_embeddings_content_sha256_format
        CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT runbook_embeddings_vector_dimensions
        CHECK (vector_dims(embedding) = embedding_dimensions),
    CONSTRAINT runbook_embeddings_identity
        UNIQUE (
            corpus_version,
            evidence_id,
            embedding_model,
            embedding_dimensions,
            embedding_format_version
        ),
    CONSTRAINT runbook_embeddings_chunk_identity
        UNIQUE (
            corpus_version,
            document_id,
            document_version,
            chunk_id,
            embedding_model,
            embedding_dimensions,
            embedding_format_version
        )
);

CREATE INDEX runbook_embeddings_profile_idx
    ON runbook_embeddings (
        corpus_version,
        embedding_model,
        embedding_dimensions,
        embedding_format_version
    );
