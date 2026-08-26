package dev.shirwac.incidentdetective.rag;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@Profile("rag")
public final class JdbcRunbookVectorStore implements RunbookVectorStore {

    private static final String PROFILE_WHERE = """
            corpus_version = :corpusVersion
            AND embedding_model = :embeddingModel
            AND embedding_dimensions = :embeddingDimensions
            AND embedding_format_version = :embeddingFormatVersion
            """;

    private final JdbcClient jdbc;

    public JdbcRunbookVectorStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean containsCurrent(
            String corpusVersion,
            RunbookCorpusEntry entry,
            RagProperties profile
    ) {
        Long count = profileQuery("""
                SELECT COUNT(*)
                FROM runbook_embeddings
                WHERE %s
                  AND evidence_id = :evidenceId
                  AND content_sha256 = :contentSha256
                """.formatted(PROFILE_WHERE), corpusVersion, profile)
                .param("evidenceId", entry.evidenceId())
                .param("contentSha256", entry.contentSha256())
                .query(Long.class)
                .single();
        return count != null && count == 1;
    }

    @Override
    public void upsert(
            String corpusVersion,
            RunbookCorpusEntry entry,
            RagProperties profile,
            EmbeddingResult embedding
    ) {
        requireDimensions(embedding.values(), profile);
        profileQuery("""
                INSERT INTO runbook_embeddings (
                    corpus_version,
                    evidence_id,
                    document_id,
                    document_version,
                    chunk_id,
                    title,
                    display_summary,
                    source_ref,
                    body,
                    content_sha256,
                    embedding_model,
                    embedding_dimensions,
                    embedding_format_version,
                    embedding,
                    billable_characters,
                    input_tokens,
                    embedding_latency_ms
                ) VALUES (
                    :corpusVersion,
                    :evidenceId,
                    :documentId,
                    :documentVersion,
                    :chunkId,
                    :title,
                    :displaySummary,
                    :sourceRef,
                    :body,
                    :contentSha256,
                    :embeddingModel,
                    :embeddingDimensions,
                    :embeddingFormatVersion,
                    CAST(:embedding AS vector),
                    :billableCharacters,
                    :inputTokens,
                    :embeddingLatencyMs
                )
                ON CONFLICT (
                    corpus_version,
                    evidence_id,
                    embedding_model,
                    embedding_dimensions,
                    embedding_format_version
                ) DO UPDATE SET
                    document_id = EXCLUDED.document_id,
                    document_version = EXCLUDED.document_version,
                    chunk_id = EXCLUDED.chunk_id,
                    title = EXCLUDED.title,
                    display_summary = EXCLUDED.display_summary,
                    source_ref = EXCLUDED.source_ref,
                    body = EXCLUDED.body,
                    content_sha256 = EXCLUDED.content_sha256,
                    embedding = EXCLUDED.embedding,
                    billable_characters = EXCLUDED.billable_characters,
                    input_tokens = EXCLUDED.input_tokens,
                    embedding_latency_ms = EXCLUDED.embedding_latency_ms,
                    embedded_at = CURRENT_TIMESTAMP
                """, corpusVersion, profile)
                .param("evidenceId", entry.evidenceId())
                .param("documentId", entry.documentId())
                .param("documentVersion", entry.documentVersion())
                .param("chunkId", entry.chunkId())
                .param("title", entry.title())
                .param("displaySummary", entry.displaySummary())
                .param("sourceRef", entry.sourceRef())
                .param("body", entry.text())
                .param("contentSha256", entry.contentSha256())
                .param("embedding", vectorLiteral(embedding.values()))
                .param("billableCharacters", embedding.billableCharacters())
                .param("inputTokens", embedding.inputTokens())
                .param("embeddingLatencyMs", embedding.latencyMs())
                .update();
    }

    @Override
    public List<RunbookSearchHit> search(
            String corpusVersion,
            RagProperties profile,
            List<Float> queryEmbedding,
            int topK,
            double minimumSimilarity
    ) {
        requireDimensions(queryEmbedding, profile);
        if (topK < 1 || topK > 20) {
            throw new IllegalArgumentException("topK must be between 1 and 20");
        }
        if (!Double.isFinite(minimumSimilarity)
                || minimumSimilarity < -1
                || minimumSimilarity > 1) {
            throw new IllegalArgumentException(
                    "minimumSimilarity must be finite and between -1 and 1"
            );
        }
        String vector = vectorLiteral(queryEmbedding);
        return profileQuery("""
                SELECT
                    evidence_id,
                    document_id,
                    document_version,
                    chunk_id,
                    title,
                    display_summary,
                    source_ref,
                    body,
                    1 - (embedding <=> CAST(:queryEmbedding AS vector)) AS cosine_similarity
                FROM runbook_embeddings
                WHERE %s
                  AND 1 - (embedding <=> CAST(:queryEmbedding AS vector)) >= :minimumSimilarity
                ORDER BY
                    embedding <=> CAST(:queryEmbedding AS vector),
                    evidence_id
                LIMIT :topK
                """.formatted(PROFILE_WHERE), corpusVersion, profile)
                .param("queryEmbedding", vector)
                .param("minimumSimilarity", minimumSimilarity)
                .param("topK", topK)
                .query(this::mapHit)
                .list();
    }

    @Override
    public List<String> documentIds(
            String corpusVersion,
            RagProperties profile
    ) {
        return profileQuery("""
                SELECT DISTINCT document_id
                FROM runbook_embeddings
                WHERE %s
                ORDER BY document_id
                """.formatted(PROFILE_WHERE), corpusVersion, profile)
                .query(String.class)
                .list();
    }

    @Override
    public long count(
            String corpusVersion,
            RagProperties profile
    ) {
        Long count = profileQuery("""
                SELECT COUNT(*)
                FROM runbook_embeddings
                WHERE %s
                """.formatted(PROFILE_WHERE), corpusVersion, profile)
                .query(Long.class)
                .single();
        return count == null ? 0 : count;
    }

    private JdbcClient.StatementSpec profileQuery(
            String sql,
            String corpusVersion,
            RagProperties profile
    ) {
        return jdbc.sql(sql)
                .param("corpusVersion", corpusVersion)
                .param("embeddingModel", profile.embeddingModel())
                .param("embeddingDimensions", profile.embeddingDimensions())
                .param("embeddingFormatVersion", profile.embeddingFormatVersion());
    }

    private RunbookSearchHit mapHit(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new RunbookSearchHit(
                new RunbookCorpusEntry(
                        resultSet.getString("evidence_id"),
                        resultSet.getString("document_id"),
                        resultSet.getString("document_version"),
                        resultSet.getString("chunk_id"),
                        resultSet.getString("title"),
                        resultSet.getString("display_summary"),
                        resultSet.getString("source_ref"),
                        resultSet.getString("body")
                ),
                resultSet.getDouble("cosine_similarity")
        );
    }

    private void requireDimensions(
            List<Float> values,
            RagProperties profile
    ) {
        if (values == null || values.size() != profile.embeddingDimensions()) {
            throw new IllegalArgumentException(
                    "embedding dimensions did not match the active profile"
            );
        }
        if (values.stream().anyMatch(value -> value == null || !Float.isFinite(value))) {
            throw new IllegalArgumentException("embedding values must be finite");
        }
    }

    private String vectorLiteral(List<Float> values) {
        return values.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }
}
