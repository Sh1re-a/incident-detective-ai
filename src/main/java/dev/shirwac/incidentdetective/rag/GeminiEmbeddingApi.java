package dev.shirwac.incidentdetective.rag;

import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;

public interface GeminiEmbeddingApi {

    EmbedContentResponse embed(
            String model,
            String input,
            EmbedContentConfig config
    );
}
