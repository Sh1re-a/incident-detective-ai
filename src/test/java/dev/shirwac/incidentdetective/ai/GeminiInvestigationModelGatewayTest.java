package dev.shirwac.incidentdetective.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InterruptedIOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiInvestigationModelGatewayTest {

    @Test
    void recognizesTheSdkInterruptedIoTimeout() {
        assertTrue(GeminiInvestigationModelGateway.isTimeout(
                new InterruptedIOException("timeout")
        ));
    }

    @Test
    void recognizesATimeoutNestedByTheSdk() {
        IOException wrapper = new IOException(
                "request failed",
                new InterruptedIOException("timeout")
        );

        assertTrue(GeminiInvestigationModelGateway.isTimeout(wrapper));
    }

    @Test
    void doesNotTreatEveryIoFailureAsATimeout() {
        assertFalse(GeminiInvestigationModelGateway.isTimeout(
                new IOException("connection refused")
        ));
    }
}
