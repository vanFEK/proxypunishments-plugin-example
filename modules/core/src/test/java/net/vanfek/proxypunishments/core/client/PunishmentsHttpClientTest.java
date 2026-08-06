package net.vanfek.proxypunishments.core.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.vanfek.proxypunishments.api.ActivePunishmentView;
import net.vanfek.proxypunishments.api.AltView;
import net.vanfek.proxypunishments.api.PunishmentHistoryEntry;
import net.vanfek.proxypunishments.api.PunishmentKind;
import net.vanfek.proxypunishments.core.config.PluginConfig;
import net.vanfek.proxypunishments.core.metrics.PluginMetrics;
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
    private static final String ADDRESS = "203.0.113.7";

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

        this.client = new PunishmentsHttpClient(config, new PluginMetrics());
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
    @DisplayName("IP bans use the contract's address endpoints and carry the address in the body")
    void speaksTheIpBanEndpoints() throws Exception {
        this.status = 200;
        this.body = ipBan();

        Optional<ActivePunishmentView> active = this.client.findActiveIpBan(ADDRESS).get();

        assertThat(active).isPresent();
        assertThat(active.orElseThrow().isIpBan()).isTrue();
        assertThat(active.orElseThrow().address()).contains(ADDRESS);
        assertThat(this.requests.getFirst().uri()).isEqualTo("/api/v1/punishments/ip/" + ADDRESS + "/active");

        this.status = 201;

        this.client.issueIpBan(ADDRESS, PLAYER, ADMIN, "Admin", "ban evasion", Duration.ofHours(1)).get();

        RecordedRequest issued = this.requests.getLast();

        assertThat(issued.method()).isEqualTo("POST");
        assertThat(issued.uri()).isEqualTo("/api/v1/punishments");
        assertThat(issued.body()).contains("\"kind\":\"IP_BAN\"");
        assertThat(issued.body()).contains("\"ipAddress\":\"" + ADDRESS + "\"");

        this.status = 204;
        this.body = "";

        this.client.liftIpBan(ADDRESS, ADMIN, "Admin", "appeal accepted").get();

        RecordedRequest lifted = this.requests.getLast();

        assertThat(lifted.method()).isEqualTo("DELETE");
        assertThat(lifted.uri()).isEqualTo("/api/v1/punishments/ip/" + ADDRESS);
        assertThat(lifted.body()).contains("\"liftedByName\":\"Admin\"");
    }

    @Test
    @DisplayName("alts come back from the players endpoint, newest first, with the shared-address flag")
    void readsAltsThroughTheContractEndpoint() throws Exception {
        this.status = 200;
        this.body = """
                {"data": [
                  {"uuid": "22222222-3333-4444-5555-666666666666", "login": "Alex",
                   "lastSeenAt": "2026-01-01T00:00:00Z", "sharedIp": true}
                ]}
                """;

        List<AltView> alts = this.client.alts(PLAYER, 20).get();

        assertThat(alts).singleElement().extracting(AltView::login).isEqualTo("Alex");
        assertThat(alts.getFirst().sharedIp()).isTrue();
        assertThat(this.requests.getFirst().uri()).isEqualTo("/api/v1/players/" + PLAYER + "/alts?limit=20");
    }

    @Test
    @DisplayName("the remember call carries the address next to the name, and omits it when there is none")
    void remembersThePlayerAddress() throws Exception {
        this.status = 204;
        this.body = "";

        this.client.rememberPlayer(PLAYER, "Steve", ADDRESS).get();

        assertThat(this.requests.getFirst().uri()).isEqualTo("/api/v1/known-players");
        assertThat(this.requests.getFirst().body()).contains("\"ip\":\"" + ADDRESS + "\"");

        this.client.rememberPlayer(PLAYER, "Steve", null).get();

        assertThat(this.requests.getLast().body()).doesNotContain("\"ip\"");
    }

    @Test
    @DisplayName("an unreachable host fails the future rather than throwing on the calling thread")
    void reportsConnectionFailuresThroughTheFuture() {
        PluginConfig.Service config = new PluginConfig.Service();
        config.baseUrl = "http://127.0.0.1:1";
        config.token = "test-token";
        config.connectTimeout = Duration.ofMillis(200);
        config.requestTimeout = Duration.ofMillis(500);

        try (PunishmentsHttpClient unreachable = new PunishmentsHttpClient(config, new PluginMetrics())) {
            assertThatThrownBy(() -> unreachable.findActive(PLAYER, PunishmentKind.BAN).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(PunishmentsHttpException.class);
        }
    }

    private static String ipBan() {
        return """
                {
                  "id": 9,
                  "playerUuid": "11111111-2222-3333-4444-555555555555",
                  "kind": "IP_BAN",
                  "ipAddress": "203.0.113.7",
                  "issuedByUuid": "00000000-0000-0000-0000-000000000000",
                  "issuedByName": "Console",
                  "reason": "ban evasion",
                  "issuedAt": "2026-01-01T00:00:00Z",
                  "expiresAt": null,
                  "type": "PERMANENT"
                }
                """;
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
