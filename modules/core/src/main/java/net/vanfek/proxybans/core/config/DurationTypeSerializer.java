package net.vanfek.proxybans.core.config;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.format.DateTimeParseException;

/**
 * Reads ISO-8601 durations ({@code PT30S}, {@code PT30M}) from YAML. Configurate has no built-in
 * serializer for {@link Duration}, and without this the whole config silently fails to deserialize.
 */
public final class DurationTypeSerializer implements TypeSerializer<Duration> {

    public static final DurationTypeSerializer INSTANCE = new DurationTypeSerializer();

    private DurationTypeSerializer() {
    }

    @Override
    public Duration deserialize(Type type, ConfigurationNode node) throws SerializationException {
        String raw = node.getString();

        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return Duration.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new SerializationException(node, type, "Invalid ISO-8601 duration: " + raw, ex);
        }
    }

    @Override
    public void serialize(Type type, @Nullable Duration value, ConfigurationNode node) throws SerializationException {
        if (value == null) {
            node.set(null);

            return;
        }

        node.set(value.toString());
    }

}
