package dev.shirwac.incidentdetective.ai;

import com.google.genai.types.ThinkingLevel;

public enum GeminiThinkingLevel {
    MINIMAL(ThinkingLevel.Known.MINIMAL),
    LOW(ThinkingLevel.Known.LOW),
    MEDIUM(ThinkingLevel.Known.MEDIUM),
    HIGH(ThinkingLevel.Known.HIGH);

    private final ThinkingLevel.Known sdkValue;

    GeminiThinkingLevel(ThinkingLevel.Known sdkValue) {
        this.sdkValue = sdkValue;
    }

    ThinkingLevel.Known sdkValue() {
        return sdkValue;
    }
}
