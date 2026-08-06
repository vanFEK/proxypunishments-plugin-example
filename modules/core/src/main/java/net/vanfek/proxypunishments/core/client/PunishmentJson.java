package net.vanfek.proxypunishments.core.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.vanfek.proxypunishments.api.ActivePunishmentView;
import net.vanfek.proxypunishments.api.BanType;
import net.vanfek.proxypunishments.api.PunishmentHistoryEntry;
import net.vanfek.proxypunishments.api.PunishmentKind;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

final class PunishmentJson {

    private PunishmentJson() {
    }

    static JsonObject parseObject(String body) {
        try {
            JsonElement element = JsonParser.parseString(body);

            if (!element.isJsonObject()) {
                throw new PunishmentsHttpException("proxypunishments-service returned a non-object JSON payload", null);
            }

            return element.getAsJsonObject();
        } catch (JsonSyntaxException ex) {
            throw new PunishmentsHttpException("proxypunishments-service returned a malformed JSON payload", ex);
        }
    }

    static <T> List<T> parseArray(String body, Function<JsonObject, T> mapper) {
        JsonObject json = parseObject(body);

        if (!json.has("data") || !json.get("data").isJsonArray()) {
            throw new PunishmentsHttpException("proxypunishments-service response is missing the 'data' array", null);
        }

        List<T> result = new ArrayList<>();

        json.getAsJsonArray("data").forEach(element -> result.add(mapper.apply(element.getAsJsonObject())));

        return result;
    }

    static ActivePunishmentView parseActivePunishment(JsonObject json) {
        return new ActivePunishmentView(
                readLong(json, "id"), readUuid(json, "playerUuid"), readNullableString(json, "playerName"),
                readEnum(json, "kind", PunishmentKind.class), readEnum(json, "type", BanType.class),
                readUuid(json, "issuedByUuid"), readString(json, "issuedByName"),
                readString(json, "reason"), readInstant(json, "issuedAt"), readNullableInstant(json, "expiresAt")
        );
    }

    static PunishmentHistoryEntry parseHistoryEntry(JsonObject json) {
        return new PunishmentHistoryEntry(
                readUuid(json, "playerUuid"), readUuid(json, "issuedByUuid"), readString(json, "issuedByName"),
                readEnum(json, "action", PunishmentHistoryEntry.Action.class), readString(json, "reason"),
                readInstant(json, "happenedAt"), readNullableInstant(json, "expiresAt")
        );
    }

    private static boolean isPresent(JsonObject json, String field) {
        return json.has(field) && !json.get(field).isJsonNull();
    }

    private static String readString(JsonObject json, String field) {
        if (!isPresent(json, field)) {
            throw new PunishmentsHttpException("proxypunishments-service response is missing the '" + field + "' field", null);
        }

        return json.get(field).getAsString();
    }

    private static @Nullable String readNullableString(JsonObject json, String field) {
        return isPresent(json, field) ? json.get(field).getAsString() : null;
    }

    private static long readLong(JsonObject json, String field) {
        if (!isPresent(json, field)) {
            throw new PunishmentsHttpException("proxypunishments-service response is missing the '" + field + "' field", null);
        }

        return json.get(field).getAsLong();
    }

    static UUID readUuid(JsonObject json, String field) {
        try {
            return UUID.fromString(readString(json, field));
        } catch (IllegalArgumentException ex) {
            throw new PunishmentsHttpException("proxypunishments-service returned an invalid UUID in '" + field + "'", ex);
        }
    }

    private static Instant readInstant(JsonObject json, String field) {
        try {
            return Instant.parse(readString(json, field));
        } catch (DateTimeParseException ex) {
            throw new PunishmentsHttpException("proxypunishments-service returned an invalid timestamp in '" + field + "'", ex);
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
            throw new PunishmentsHttpException("proxypunishments-service returned an unknown " + field + ": " + raw, ex);
        }
    }

}
