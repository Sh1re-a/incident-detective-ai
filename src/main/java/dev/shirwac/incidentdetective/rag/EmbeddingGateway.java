package dev.shirwac.incidentdetective.rag;

public interface EmbeddingGateway {

    EmbeddingResult embedQuery(String query);

    EmbeddingResult embedDocument(String title, String text);
}
