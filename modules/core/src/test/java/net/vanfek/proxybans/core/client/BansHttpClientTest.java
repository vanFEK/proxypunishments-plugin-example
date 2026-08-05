package net.vanfek.proxybans.core.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.vanfek.proxybans.api.ActiveBanView;
import net.vanfek.proxybans.core.config.PluginConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the client against a real socket rather than a mock, because the things that broke here
 * before — unescaped query strings, non-JSON error pages, synchronous throws that escaped the future
 * — all live below the level a mocked HTTP client would reach.
 */
class BansHttpClientTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private HttpServer server;
    private BansHttpClient client;
    private final List<String> requestedUris = new CopyOnWriteArrayList<>();

    private volatile int status = 200;
    private volatile String body = "{}";

    @BeforeEach
    void setUp() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", this::respond);
        this.server.start();

        PluginConfig.Service config = new PluginConfig.Service();
        config.baseUrl = "http://127.0.0.1:" + this.server.getAddress().getPort();
        config.token = "test-token";
        config.connectTimeout = Duration.ofSeconds(2);
        config.requestTimeout = Duration.ofSeconds(5);

        this.client = new BansHttpClient(config);
    }

    @AfterEach
    void tearDown() {
        this.client.close();
        this.server.stop(0);
    }

    @Test
    @DisplayName("a name with characters that are illegal in a URI is escaped, not thrown on")
    void escapesLookupQuery() throws Exception {
        this.status = 404;

        this.client.lookup("player name&admin=1").get();

        assertThat(this.requestedUris).hasSize(1);
        assertThat(this.requestedUris.getFirst()).contains("name=player+name%26admin%3D1");
    }

    @Test
    void mapsNotFoundToAnEmptyResult() throws Exception {
        this.status = 404;

        assertThat(this.client.findActiveBan(PLAYER).get()).isEmpty();
    }

    @Test
    @DisplayName("an HTML error page from a proxy fails the future instead of blowing up as a JSON error")
    void wrapsNonJsonResponses() {
        this.status = 200;
        this.body = "<html><body>502 Bad Gateway</body></html>";

        assertThatThrownBy(() -> this.client.findActiveBan(PLAYER).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(BansHttpException.class);
    }

    @Test
    @DisplayName("a server error keeps the body out of the exception message and in a separate field")
    void separatesMessageFromResponseBody() {
        this.status = 500;
        this.body = "internal detail that must not reach chat";

        assertThatThrownBy(() -> this.client.findActiveBan(PLAYER).get())
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(BansHttpException.class)
                .hasMessageContaining("HTTP 500")
                .hasMessageNotContaining("internal detail");
    }

    @Test
    void parsesAnActiveBan() throws Exception {
        this.status = 200;
        this.body = """
                {
                  "id": 7,
                  "playerUuid": "11111111-2222-3333-4444-555555555555",
                  "playerName": "Steve",
                  "issuedByUuid": "00000000-0000-0000-0000-000000000000",
                  "issuedByName": "Console",
                  "reason": "griefing",
                  "issuedAt": "2026-01-01T00:00:00Z",
                  "expiresAt": null,
                  "type": "PERMANENT"
                }
                """;

        Optional<ActiveBanView> active = this.client.findActiveBan(PLAYER).get();

        assertThat(active).isPresent();
        assertThat(active.orElseThrow().displayName()).isEqualTo("Steve");
        assertThat(active.orElseThrow().isPermanent()).isTrue();
        assertThat(active.orElseThrow().expiresAt()).isNull();
    }

    @Test
    @DisplayName("an unreachable host fails the future rather than throwing on the calling thread")
    void reportsConnectionFailuresThroughTheFuture() {
        PluginConfig.Service config = new PluginConfig.Service();
        config.baseUrl = "http://127.0.0.1:1";
        config.token = "test-token";
        config.connectTimeout = Duration.ofMillis(200);
        config.requestTimeout = Duration.ofMillis(500);

        try (BansHttpClient unreachable = new BansHttpClient(config)) {
            assertThatThrownBy(() -> unreachable.findActiveBan(PLAYER).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(BansHttpException.class);
        }
    }

    private void respond(HttpExchange exchange) throws IOException {
        this.requestedUris.add(exchange.getRequestURI().toString());

        byte[] payload = this.body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(this.status, payload.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

}
