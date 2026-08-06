package net.vanfek.proxypunishments.core.client;

import com.google.gson.JsonObject;
import net.vanfek.proxypunishments.api.ActivePunishmentView;
import net.vanfek.proxypunishments.api.PunishmentHistoryEntry;
import net.vanfek.proxypunishments.api.PunishmentKind;
import net.vanfek.proxypunishments.core.config.PluginConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** No method here throws synchronously: every failure arrives as a {@link PunishmentsHttpException} inside the future. */
public final class PunishmentsHttpClient implements PunishmentsClient {

    private static final String PUNISHMENTS = "/api/v1/punishments";
    private static final String WARNINGS = "/api/v1/warnings";
    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final int NOT_FOUND = 404;

    private final HttpClient http;
    private final String baseUrl;
    private final String token;
    private final Duration requestTimeout;

    public PunishmentsHttpClient(@NotNull PluginConfig.Service config) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.baseUrl = stripTrailingSlash(config.baseUrl);
        this.token = config.token;
        this.requestTimeout = config.requestTimeout;
    }

    @Override
    public @NotNull CompletableFuture<Optional<ActivePunishmentView>> findActive(@NotNull UUID playerUuid, @NotNull PunishmentKind kind) {
        String path = PUNISHMENTS + "/" + playerUuid + "/active?" + query("kind", kind);

        return get(path, response -> {
            if (response.statusCode() == NOT_FOUND) {
                return Optional.empty();
            }

            requireSuccess(response);

            return Optional.of(PunishmentJson.parseActivePunishment(PunishmentJson.parseObject(response.body())));
        });
    }

    @Override
    public @NotNull CompletableFuture<ActivePunishmentView> issue(
            @NotNull UUID playerUuid, @NotNull PunishmentKind kind,
            @NotNull UUID issuedByUuid, @NotNull String issuedByName,
            @NotNull String reason, @Nullable Duration duration
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("playerUuid", playerUuid.toString());
        payload.addProperty("kind", kind.name());
        payload.addProperty("issuedByUuid", issuedByUuid.toString());
        payload.addProperty("issuedByName", issuedByName);
        payload.addProperty("reason", reason);

        if (duration != null) {
            payload.addProperty("durationSeconds", duration.toSeconds());
        }

        return send(
                PUNISHMENTS, builder -> builder.POST(body(payload)).header("Content-Type", CONTENT_TYPE),
                response -> {
                    requireSuccess(response);

                    return PunishmentJson.parseActivePunishment(PunishmentJson.parseObject(response.body()));
                }
        );
    }

    @Override
    public @NotNull CompletableFuture<Void> lift(
            @NotNull UUID playerUuid, @NotNull PunishmentKind kind,
            @NotNull UUID liftedByUuid, @NotNull String liftedByName, @NotNull String reason
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("liftedByUuid", liftedByUuid.toString());
        payload.addProperty("liftedByName", liftedByName);
        payload.addProperty("reason", reason);

        String path = PUNISHMENTS + "/" + playerUuid + "?" + query("kind", kind);

        return send(
                path, builder -> builder.method("DELETE", body(payload)).header("Content-Type", CONTENT_TYPE),
                response -> {
                    requireSuccess(response);

                    return null;
                }
        );
    }

    @Override
    public @NotNull CompletableFuture<PunishmentHistoryEntry> warn(
            @NotNull UUID playerUuid, @NotNull UUID issuedByUuid,
            @NotNull String issuedByName, @NotNull String reason
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("playerUuid", playerUuid.toString());
        payload.addProperty("issuedByUuid", issuedByUuid.toString());
        payload.addProperty("issuedByName", issuedByName);
        payload.addProperty("reason", reason);

        return send(
                WARNINGS, builder -> builder.POST(body(payload)).header("Content-Type", CONTENT_TYPE),
                response -> {
                    requireSuccess(response);

                    return PunishmentJson.parseHistoryEntry(PunishmentJson.parseObject(response.body()));
                }
        );
    }

    @Override
    public @NotNull CompletableFuture<List<ActivePunishmentView>> listActive(@NotNull PunishmentKind kind, int limit, int offset) {
        String path = PUNISHMENTS + "?" + query("kind", kind) + "&" + query("limit", limit) + "&" + query("offset", offset);

        return get(path, response -> {
            requireSuccess(response);

            return PunishmentJson.parseArray(response.body(), PunishmentJson::parseActivePunishment);
        });
    }

    @Override
    public @NotNull CompletableFuture<List<PunishmentHistoryEntry>> history(@NotNull UUID playerUuid, int limit, int offset) {
        String path = PUNISHMENTS + "/" + playerUuid + "/history?" + query("limit", limit) + "&" + query("offset", offset);

        return get(path, response -> {
            if (response.statusCode() == NOT_FOUND) {
                return List.of();
            }

            requireSuccess(response);

            return PunishmentJson.parseArray(response.body(), PunishmentJson::parseHistoryEntry);
        });
    }

    @Override
    public @NotNull CompletableFuture<Optional<UUID>> lookup(@NotNull String login) {
        String path = "/api/v1/lookup?" + query("name", login);

        return get(path, response -> {
            if (response.statusCode() == NOT_FOUND) {
                return Optional.empty();
            }

            requireSuccess(response);

            return Optional.of(PunishmentJson.readUuid(PunishmentJson.parseObject(response.body()), "uuid"));
        });
    }

    @Override
    public @NotNull CompletableFuture<Void> rememberPlayer(@NotNull UUID uuid, @NotNull String login) {
        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", uuid.toString());
        payload.addProperty("login", login);

        return send(
                "/api/v1/known-players", builder -> builder.POST(body(payload)).header("Content-Type", CONTENT_TYPE),
                response -> {
                    requireSuccess(response);

                    return null;
                }
        );
    }

    @Override
    public void close() {
        this.http.close();
    }

    private <T> CompletableFuture<T> get(String path, Function<HttpResponse<String>, T> mapper) {
        return send(path, HttpRequest.Builder::GET, mapper);
    }

    private <T> CompletableFuture<T> send(
            String path,
            Function<HttpRequest.Builder, HttpRequest.Builder> configurer, Function<HttpResponse<String>, T> mapper
    ) {
        HttpRequest request;

        try {
            request = configurer.apply(baseRequest(path)).build();
        } catch (RuntimeException ex) {
            return CompletableFuture.failedFuture(new PunishmentsHttpException("Could not build request for " + path, ex));
        }

        return this.http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        throw new PunishmentsHttpException("HTTP request to proxypunishments-service failed", throwable);
                    }

                    return mapper.apply(response);
                });
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder(URI.create(this.baseUrl + path))
                .timeout(this.requestTimeout)
                .header(SERVICE_TOKEN_HEADER, this.token)
                .header("Accept", "application/json");
    }

    private static String query(String name, Object value) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
    }

    private static HttpRequest.BodyPublisher body(JsonObject payload) {
        return HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8);
    }

    private static void requireSuccess(HttpResponse<String> response) {
        int status = response.statusCode();

        if (status >= 200 && status < 300) {
            return;
        }

        throw new PunishmentsHttpException(status, "proxypunishments-service returned HTTP " + status, response.body());
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

}
