package ledger.api;

import io.javalin.Javalin;
import ledger.memory.InMemoryIdempotencyStore;
import ledger.memory.InMemoryLedgerStore;
import ledger.service.LedgerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

abstract class ApiTestSupport {

    private final HttpClient client = HttpClient.newHttpClient();
    private Javalin app;

    @BeforeEach
    void start() {
        app = LedgerApi.create(new LedgerService(new InMemoryLedgerStore(), new InMemoryIdempotencyStore())).start(0);
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    void account(String id, long balance) throws Exception {
        var response = post("/accounts", null,
                "{\"id\":\"" + id + "\",\"initialBalance\":" + balance + ",\"currency\":\"EUR\"}");
        assertThat(response.statusCode()).isEqualTo(201);
    }

    HttpResponse<String> post(String path, String idempotencyKey, String json) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + app.port() + path);
    }
}
