package net.vanfek.proxybans.velocity.command.punish;

import net.vanfek.proxybans.core.duration.DurationParser;
import net.vanfek.proxybans.core.duration.ParsedDuration;
import org.jetbrains.annotations.NotNull;

// Parsed from one greedy argument: a word-typed duration argument would reject "/ban Steve cheating with hacks".
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
