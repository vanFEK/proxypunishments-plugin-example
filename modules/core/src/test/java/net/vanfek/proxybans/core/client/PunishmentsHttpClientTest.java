package net.vanfek.proxybans.core.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.vanfek.proxybans.api.ActivePunishmentView;
import net.vanfek.proxybans.api.PunishmentHistoryEntry;
import net.vanfek.proxybans.api.PunishmentKind;
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

class PunishmentsHttpClientTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private HttpServer server;
    private PunishmentsHttpClient client;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

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

        this.client = new PunishmentsHttpClient(config);
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

        assertThat(this.requests).hasSize(1);
        assertThat(this.requests.getFirst().uri()).contains("name=player+name%26admin%3D1");
    }

    @Test
    void mapsNotFoundToAnEmptyResult() throws Exception {
        this.status = 404;

        assertThat(this.client.findActive(PLAYER, PunishmentKind.BAN).get()).isEmpty();
    }

    @Test
    @DisplayName("an HTML error page from a proxy fails the future instead of blowing up as a JSON error")
    void wrapsNonJsonResponses() {
        this.status = 200;
        this.body = "<html><body>502 Bad Gateway</body></html>";

        assertThatThrownBy(() -> this.client.findActive(PLAYER, PunishmentKind.BAN).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(PunishmentsHttpException.class);
    }

    @Test
    @DisplayName("a server error keeps the body out of the exception message and in a separate field")
    void separatesMessageFromResponseBody() {
        this.status = 500;
        this.body = "internal detail that must not reach chat";

        assertThatThrownBy(() -> this.client.findActive(PLAYER, PunishmentKind.BAN).get())
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(PunishmentsHttpException.class)
                .hasMessageContaining("HTTP 500")
                .hasMessageNotContaining("internal detail");
    }

    @Test
    void parsesAnActiveBan() throws Exception {
        this.status = 200;
        this.body = activePunishment("BAN", "PERMANENT");

        Optional<ActivePunishmentView> active = this.client.findActive(PLAYER, PunishmentKind.BAN).get();

        assertThat(active).isPresent();
        assertThat(active.orElseThrow().displayName()).isEqualTo("Steve");
        assertThat(active.orElseThrow().isPermanent()).isTrue();
        assertThat(active.orElseThrow().expiresAt()).isNull();
        assertThat(this.requests.getFirst().uri()).isEqualTo("/api/v1/punishments/" + PLAYER + "/active?kind=BAN");
    }

    @Test
    @DisplayName("looking a mute up asks the contract's active endpoint with kind=MUTE")
    void findsAnActiveMuteThroughTheContractEndpoint() throws Exception {
        this.status = 200;
        this.body = activePunishment("MUTE", "PERMANENT");

        Optional<ActivePunishmentView> active = this.client.findActive(PLAYER, PunishmentKind.MUTE).get();

        assertThat(active).isPresent();
        assertThat(active.orElseThrow().kind()).isEqualTo(PunishmentKind.MUTE);
        assertThat(active.orElseThrow().isMute()).isTrue();
        assertThat(this.requests.getFirst().uri()).isEqualTo("/api/v1/punishments/" + PLAYER + "/active?kind=MUTE");
    }

    @Test
    @DisplayName("issuing a mute posts the contract's punishment body with its kind")
    void issuesAMuteThroughTheContractEndpoint() throws Exception {
        this.status = 201;
        this.body = activePunishment("MUTE", "TEMPORARY");

        this.client.issue(PLAYER, PunishmentKind.MUTE, ADMIN, "Admin", "spam", Duration.ofHours(1)).get();

        RecordedRequest request = this.requests.getFirst();

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri()).isEqualTo("/api/v1/punishments");
        assertThat(request.body()).contains("\"kind\":\"MUTE\"");
        assertThat(request.body()).contains("\"playerUuid\":\"" + PLAYER + "\"");
        assertThat(request.body()).contains("\"durationSeconds\":3600");
    }

    @Test
    @DisplayName("lifting a mute deletes against the contract's endpoint with kind=MUTE")
    void liftsAMuteThroughTheContractEndpoint() throws Exception {
        this.status = 204;
        this.body = "";

        this.client.lift(PLAYER, PunishmentKind.MUTE, ADMIN, "Admin", "appeal accepted").get();

        RecordedRequest request = this.requests.getFirst();

        assertThat(request.method()).isEqualTo("DELETE");
        assertThat(request.uri()).isEqualTo("/api/v1/punishments/" + PLAYER + "?kind=MUTE");
        assertThat(request.body()).contains("\"liftedByName\":\"Admin\"");
    }

    @Test
    @DisplayName("a warning goes to its own endpoint and comes back as a history entry")
    void warnsThroughTheContractEndpoint() throws Exception {
        this.status = 201;
        this.body = """
                {
                  "playerUuid": "11111111-2222-3333-4444-555555555555",
                  "issuedByUuid": "00000000-0000-0000-0000-000000000000",
                  "issuedByName": "Admin",
                  "action": "WARN",
                  "reason": "spam",
                  "happenedAt": "2026-01-01T00:00:00Z",
                  "expiresAt": null
                }
                """;

        PunishmentHistoryEntry entry = this.client.warn(PLAYER, ADMIN, "Admin", "spam").get();

        RecordedRequest request = this.requests.getFirst();

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri()).isEqualTo("/api/v1/warnings");
        assertThat(request.body()).contains("\"reason\":\"spam\"");
        assertThat(entry.action()).isEqualTo(PunishmentHistoryEntry.Action.WARN);
        assertThat(entry.expiry()).isEmpty();
    }

    @Test
    @DisplayName("listing mutes and reading history use the contract's paged endpoints")
    void listsAndReadsHistoryThroughTheContractEndpoints() throws Exception {
        this.status = 200;
        this.body = "{\"data\": [" + activePunishment("MUTE", "PERMANENT") + "]}";

        List<ActivePunishmentView> mutes = this.client.listActive(PunishmentKind.MUTE, 25, 50).get();

        assertThat(mutes).singleElement().extracting(ActivePunishmentView::kind).isEqualTo(PunishmentKind.MUTE);
        assertThat(this.requests.getFirst().uri()).isEqualTo("/api/v1/punishments?kind=MUTE&limit=25&offset=50");

        this.body = "{\"data\": []}";

        this.client.history(PLAYER, 10, 0).get();

        assertThat(this.requests.getLast().uri()).isEqualTo("/api/v1/punishments/" + PLAYER + "/history?limit=10&offset=0");
    }

    @Test
    @DisplayName("an unreachable host fails the future rather than throwing on the calling thread")
    void reportsConnectionFailuresThroughTheFuture() {
        PluginConfig.Service config = new PluginConfig.Service();
        config.baseUrl = "http://127.0.0.1:1";
        config.token = "test-token";
        config.connectTimeout = Duration.ofMillis(200);
        config.requestTimeout = Duration.ofMillis(500);

        try (PunishmentsHttpClient unreachable = new PunishmentsHttpClient(config)) {
            assertThatThrownBy(() -> unreachable.findActive(PLAYER, PunishmentKind.BAN).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(PunishmentsHttpException.class);
        }
    }

    private static String activePunishment(String kind, String type) {
        return """
                {
                  "id": 7,
                  "playerUuid": "11111111-2222-3333-4444-555555555555",
                  "playerName": "Steve",
                  "kind": "%s",
                  "issuedByUuid": "00000000-0000-0000-0000-000000000000",
                  "issuedByName": "Console",
                  "reason": "griefing",
                  "issuedAt": "2026-01-01T00:00:00Z",
                  "expiresAt": null,
                  "type": "%s"
                }
                """.formatted(kind, type);
    }

    private void respond(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        this.requests.add(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().toString(), requestBody));

        byte[] payload = this.body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add("Content-Type", "application/json");

        if (payload.length == 0) {
            exchange.sendResponseHeaders(this.status, -1);
            exchange.close();

            return;
        }

        exchange.sendResponseHeaders(this.status, payload.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private record RecordedRequest(String method, String uri, String body) {

    }

}
