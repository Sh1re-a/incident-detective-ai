package dev.shirwac.incidentdetective.ai;

/** Selects the Google Gen AI transport without changing model contracts. */
public enum GoogleGenAiProvider {
    DEVELOPER_API("developer_api", "api_key"),
    VERTEX_AI("vertex_ai", "adc");

    private final String transport;
    private final String authenticationMode;

    GoogleGenAiProvider(String transport, String authenticationMode) {
        this.transport = transport;
        this.authenticationMode = authenticationMode;
    }

    public String transport() {
        return transport;
    }

    public String authenticationMode() {
        return authenticationMode;
    }
}
