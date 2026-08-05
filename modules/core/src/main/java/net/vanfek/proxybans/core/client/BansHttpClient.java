package net.vanfek.proxybans.core.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.vanfek.proxybans.api.ActiveBanView;
import net.vanfek.proxybans.api.BanType;
import net.vanfek.proxybans.api.PunishmentHistoryEntry;
import net.vanfek.proxybans.core.config.PluginConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Thin asynchronous client for the bans service.
 *
 * <p>Every public method returns a future that fails with {@link BansHttpException} and never throws
 * synchronously — callers run inside Velocity event tasks and command handlers, where a synchronous
 * throw would escape the future's error handling entirely.
 */
public final class BansHttpClient implements BansClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final int NOT_FOUND = 404;

    private final HttpClient http;
    private final String baseUrl;
    private final String token;
    private final Duration requestTimeout;

    public BansHttpClient(@NotNull PluginConfig.Service config) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.baseUrl = stripTrailingSlash(config.baseUrl);
        this.token = config.token;
        this.requestTimeout = config.requestTimeout;
    }

    @Override
    public @NotNull CompletableFuture<Optional<ActiveBanView>> findActiveBan(@NotNull UUID uuid) {
        return get("/api/v1/bans/" + uuid + "/active", response -> {
            if (response.statusCode() == NOT_FOUND) {
                return Optional.empty();
            }

            requireSuccess(response);

            return Optional.of(parseActiveBan(parseObject(response.body())));
        });
    }

    @Override
    public @NotNull CompletableFuture<ActiveBanView> issueBan(
            @NotNull UUID playerUuid,
            @NotNull UUID issuedByUuid,
            @NotNull String issuedByName,
            @NotNull String reason,
            @Nullable Duration duration
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("playerUuid", playerUuid.toString());
        payload.addProperty("issuedByUuid", issuedByUuid.toString());
        payload.addProperty("issuedByName", issuedByName);
        payload.addProperty("reason", reason);

        if (duration != null) {
            payload.addProperty("durationSeconds", duration.toSeconds());
        }

        return send(
                "/api/v1/bans",
                builder -> builder.POST(body(payload)).header("Content-Type", CONTENT_TYPE),
                response -> {
                    requireSuccess(response);

                    return parseActiveBan(parseObject(response.body()));
                }
        );
    }

    @Override
    public @NotNull CompletableFuture<Void> liftBan(
            @NotNull UUID playerUuid,
            @NotNull UUID liftedByUuid,
            @NotNull String liftedByName,
            @NotNull String reason
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("liftedByUuid", liftedByUuid.toString());
        payload.addProperty("liftedByName", liftedByName);
        payload.addProperty("reason", reason);

        return send(
                "/api/v1/bans/" + playerUuid,
                builder -> builder.method("DELETE", body(payload)).header("Content-Type", CONTENT_TYPE),
                response -> {
                    requireSuccess(response);

                    return null;
                }
        );
    }

    @Override
    public @NotNull CompletableFuture<List<ActiveBanView>> listActiveBans(int limit, int offset) {
        String path = "/api/v1/bans?limit=" + limit + "&offset=" + offset;

        return get(path, response -> {
            requireSuccess(response);

            return parseArray(response.body(), BansHttpClient::parseActiveBan);
        });
    }

    @Override
    public @NotNull CompletableFuture<List<PunishmentHistoryEntry>> history(@NotNull UUID uuid, int limit, int offset) {
        String path = "/api/v1/bans/" + uuid + "/history?limit=" + limit + "&offset=" + offset;

        return get(path, response -> {
            if (response.statusCode() == NOT_FOUND) {
                return List.of();
            }

            requireSuccess(response);

            return parseArray(response.body(), BansHttpClient::parseHistoryEntry);
        });
    }

    @Override
    public @NotNull CompletableFuture<Optional<UUID>> lookup(@NotNull String login) {
        String path = "/api/v1/lookup?name=" + URLEncoder.encode(login, StandardCharsets.UTF_8);

        return get(path, response -> {
            if (response.statusCode() == NOT_FOUND) {
                return Optional.empty();
            }

            requireSuccess(response);

            return Optional.of(readUuid(parseObject(response.body()), "uuid"));
        });
    }

    @Override
    public @NotNull CompletableFuture<Void> rememberPlayer(@NotNull UUID uuid, @NotNull String login) {
        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", uuid.toString());
        payload.addProperty("login", login);

        return send(
                "/api/v1/known-players",
                builder -> builder.POST(body(payload)).header("Content-Type", CONTENT_TYPE),
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
            Function<HttpRequest.Builder, HttpRequest.Builder> configurer,
            Function<HttpResponse<String>, T> mapper
    ) {
        HttpRequest request;

        try {
            request = configurer.apply(baseRequest(path)).build();
        } catch (RuntimeException ex) {
            return CompletableFuture.failedFuture(new BansHttpException("Could not build request for " + path, ex));
        }

        return this.http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        throw new BansHttpException("HTTP request to bans-service failed", throwable);
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

    private static HttpRequest.BodyPublisher body(JsonObject payload) {
        return HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8);
    }

    private static void requireSuccess(HttpResponse<String> response) {
        int status = response.statusCode();

        if (status >= 200 && status < 300) {
            return;
        }

        throw new BansHttpException(status, "bans-service returned HTTP " + status, response.body());
    }

    private static JsonObject parseObject(String body) {
        try {
            JsonElement element = JsonParser.parseString(body);

            if (!element.isJsonObject()) {
                throw new BansHttpException("bans-service returned a non-object JSON payload", null);
            }

            return element.getAsJsonObject();
        } catch (JsonSyntaxException ex) {
            throw new BansHttpException("bans-service returned a malformed JSON payload", ex);
        }
    }

    private static <T> List<T> parseArray(String body, Function<JsonObject, T> mapper) {
        JsonObject json = parseObject(body);

        if (!json.has("data") || !json.get("data").isJsonArray()) {
            throw new BansHttpException("bans-service response is missing the 'data' array", null);
        }

        List<T> result = new ArrayList<>();

        json.getAsJsonArray("data").forEach(element -> result.add(mapper.apply(element.getAsJsonObject())));

        return result;
    }

    private static ActiveBanView parseActiveBan(JsonObject json) {
        return new ActiveBanView(
                readLong(json, "id"),
                readUuid(json, "playerUuid"),
                readNullableString(json, "playerName"),
                readUuid(json, "issuedByUuid"),
                readString(json, "issuedByName"),
                readString(json, "reason"),
                readInstant(json, "issuedAt"),
                readNullableInstant(json, "expiresAt"),
                readEnum(json, "type", BanType.class)
        );
    }

    private static PunishmentHistoryEntry parseHistoryEntry(JsonObject json) {
        return new PunishmentHistoryEntry(
                readUuid(json, "playerUuid"),
                readUuid(json, "issuedByUuid"),
                readString(json, "issuedByName"),
                readEnum(json, "action", PunishmentHistoryEntry.Action.class),
                readString(json, "reason"),
                readInstant(json, "happenedAt"),
                readNullableInstant(json, "expiresAt")
        );
    }

    private static boolean isPresent(JsonObject json, String field) {
        return json.has(field) && !json.get(field).isJsonNull();
    }

    private static String readString(JsonObject json, String field) {
        if (!isPresent(json, field)) {
            throw new BansHttpException("bans-service response is missing the '" + field + "' field", null);
        }

        return json.get(field).getAsString();
    }

    private static @Nullable String readNullableString(JsonObject json, String field) {
        return isPresent(json, field) ? json.get(field).getAsString() : null;
    }

    private static long readLong(JsonObject json, String field) {
        if (!isPresent(json, field)) {
            throw new BansHttpException("bans-service response is missing the '" + field + "' field", null);
        }

        return json.get(field).getAsLong();
    }

    private static UUID readUuid(JsonObject json, String field) {
        try {
            return UUID.fromString(readString(json, field));
        } catch (IllegalArgumentException ex) {
            throw new BansHttpException("bans-service returned an invalid UUID in '" + field + "'", ex);
        }
    }

    private static Instant readInstant(JsonObject json, String field) {
        try {
            return Instant.parse(readString(json, field));
        } catch (DateTimeParseException ex) {
            throw new BansHttpException("bans-service returned an invalid timestamp in '" + field + "'", ex);
        }
    }

    private static @Nullable Instant readNullableInstant(JsonObject json, String field) {
        return isPresent(json, field) ? readInstant(json, field) : null;
    }

    private static <E extends Enum<E>> E readEnum(JsonObject json, String field, Class<E> type) {
        String raw = readString(json, field);

        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BansHttpException("bans-service returned an unknown " + field + ": " + raw, ex);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

}
