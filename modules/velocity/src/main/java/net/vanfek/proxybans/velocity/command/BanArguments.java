package net.vanfek.proxybans.velocity.command;

import net.vanfek.proxybans.core.duration.DurationParser;
import net.vanfek.proxybans.core.duration.ParsedDuration;
import org.jetbrains.annotations.NotNull;

/**
 * Splits the free-form tail of {@code /ban <player> ...} into an optional duration and a reason.
 *
 * <p>The duration is optional and the reason is free text, so the two cannot be separate Brigadier
 * arguments: with a word-typed second argument, {@code /ban Steve cheating with hacks} would send
 * "cheating" to the duration parser and answer with "invalid duration" instead of banning anyone.
 * Here the first word is only treated as a duration when it actually looks like one.
 */
public record BanArguments(@NotNull ParsedDuration duration, @NotNull String reason) {

    public static @NotNull BanArguments parse(@NotNull String tail) {
        String trimmed = tail.trim();
        int separator = trimmed.indexOf(' ');

        String head = separator < 0 ? trimmed : trimmed.substring(0, separator);
        String rest = separator < 0 ? "" : trimmed.substring(separator + 1).trim();

        return DurationParser.tryParse(head)
                .map(duration -> new BanArguments(duration, rest))
                .orElseGet(() -> new BanArguments(ParsedDuration.permanent(), trimmed));
    }

    public boolean hasReason() {
        return !this.reason.isBlank();
    }

}
