package dev.shirwac.incidentdetective.ai;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpClientDependencyCompatibilityTest {

    @Test
    void resolvedOkHttpAndOkioCanOpenALoopbackSocket() throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );
        server.createContext("/compatibility", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(2))
                .build();
        Request request = new Request.Builder()
                .url("http://127.0.0.1:" + server.getAddress().getPort()
                        + "/compatibility")
                .build();

        try (Response response = client.newCall(request).execute()) {
            assertEquals(204, response.code());
        } finally {
            client.dispatcher().executorService().shutdownNow();
            client.connectionPool().evictAll();
            server.stop(0);
        }
    }

    @Test
    void otlpExporterUsesTheJdkSenderWithoutAddingOkHttpFive() {
        assertDoesNotThrow(() -> Class.forName(
                "io.opentelemetry.exporter.sender.jdk.internal."
                        + "JdkHttpSenderProvider"
        ));
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "io.opentelemetry.exporter.sender.okhttp.internal."
                        + "OkHttpHttpSenderProvider"
        ));

        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint("http://127.0.0.1:4318/v1/traces")
                .build();
        exporter.shutdown();
    }
}
