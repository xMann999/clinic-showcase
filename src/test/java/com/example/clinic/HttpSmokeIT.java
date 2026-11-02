package com.example.clinic;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.clinic.support.FixedClockConfiguration;
import com.example.clinic.support.TestFixtures;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test na prawdziwym porcie: Tomcat, gniazdo TCP, nagłówki HTTP/1.1, klient spoza Springa
 * (java.net.http.HttpClient). MockMvc tego nie sprawdza - działa bez sieci.
 *
 * Uruchomienie: ./mvnw -Dit.test=HttpSmokeIT verify
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(FixedClockConfiguration.class)
class HttpSmokeIT {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    @Test
    void createAndReadUserOverRealSocket() throws Exception {
        HttpResponse<String> created = client.send(
            HttpRequest.newBuilder(uri("/api/users"))
                .header("Content-Type", "application/json")
                .header("X-Request-Id", "smoke-test-0001")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"%s\",\"password\":\"Secret-123\",\"role\":\"USER\"}".formatted(
                                TestFixtures.uniqueEmail()
                            )
                    )
                )
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("X-Request-Id")).contains("smoke-test-0001");
        String location = created.headers().firstValue("Location").orElseThrow();

        HttpResponse<String> read = client.send(
            HttpRequest.newBuilder(URI.create(location)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(read.body()).doesNotContain("password");
    }

    @Test
    void apiDocsReachable() throws Exception {
        HttpResponse<String> docs = client.send(
            HttpRequest.newBuilder(uri("/v3/api-docs")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(docs.statusCode()).isEqualTo(200);
        assertThat(docs.body()).contains("\"/api/visits\"", "ApiError");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
