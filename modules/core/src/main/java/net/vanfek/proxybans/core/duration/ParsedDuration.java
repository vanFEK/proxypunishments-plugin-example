package net.vanfek.proxybans.core.duration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;

/**
 * Result of reading a duration token typed by a moderator.
 *
 * <p>A permanent ban and a timed ban are different outcomes, not "a duration that happens to be
 * missing", so they are modelled explicitly instead of leaking a bare {@code null} to callers.
 *
 * @param duration {@code null} means permanent
 */
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
