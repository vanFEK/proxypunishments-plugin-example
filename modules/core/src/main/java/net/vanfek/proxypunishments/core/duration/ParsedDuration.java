package net.vanfek.proxypunishments.core.duration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;

public record ParsedDuration(@Nullable Duration duration) {

    private static final ParsedDuration PERMANENT = new ParsedDuration(null);

    public static @NotNull ParsedDuration permanent() {
        return PERMANENT;
    }

    public static @NotNull ParsedDuration of(@NotNull Duration duration) {
        return new ParsedDuration(duration);
    }

    public boolean isPermanent() {
        return this.duration == null;
    }

    public @NotNull Optional<Duration> asOptional() {
        return Optional.ofNullable(this.duration);
    }

}
