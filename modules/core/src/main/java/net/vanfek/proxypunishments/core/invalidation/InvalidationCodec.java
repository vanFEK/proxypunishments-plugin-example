package net.vanfek.proxypunishments.core.invalidation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.vanfek.proxypunishments.api.PunishmentKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

public final class InvalidationCodec {

    private InvalidationCodec() {
    }

    public static @NotNull Optional<InvalidationMessage> decode(@Nullable String payload) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonElement element = JsonParser.parseString(payload);

            if (!element.isJsonObject()) {
                return Optional.empty();
            }

            return message(element.getAsJsonObject());
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static Optional<InvalidationMessage> message(JsonObject json) {
        String target = string(json, "target");
        String kind = string(json, "kind");

        if (target == null || kind == null) {
            return Optional.empty();
        }

        try {
            PunishmentKind parsed = PunishmentKind.valueOf(kind.toUpperCase(Locale.ROOT));
            String origin = Optional.ofNullable(string(json, "origin")).orElse("");

            return Optional.of(new InvalidationMessage(target, parsed, origin));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String string(JsonObject json, String field) {
        JsonElement element = json.get(field);

        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }

        String value = element.getAsString();

        return value.isBlank() ? null : value;
    }

}
