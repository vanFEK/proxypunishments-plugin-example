package net.vanfek.proxybans.core.duration;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses moderator-typed duration tokens such as {@code 1d}, {@code 2h30m}, {@code 3M} or {@code perm}.
 *
 * <p>Unit letters are case sensitive on purpose: {@code m} is minutes and {@code M} is months. The
 * input is therefore never lower-cased before matching — doing so silently turns "3 months" into
 * "3 minutes".
 */
public final class DurationParser {

    /**
     * Upper bound for a timed ban. Anything longer is a permanent ban in practice, and accepting it
     * only invites overflow and nonsensical expiry timestamps.
     */
    public static final Duration MAX = Duration.ofDays(3650);

    private static final Pattern TOKEN = Pattern.compile("(\\d{1,12})\\s*([smhdwMy])");
    private static final Pattern SHAPE = Pattern.compile("(\\d{1,12}\\s*[smhdwMy])+");
    private static final Set<String> PERMANENT_TOKENS = Set.of("perm", "permanent", "forever", "never", "0");

    private DurationParser() {
    }

    /**
     * Strict parse for input that is already known to be a duration.
     *
     * @throws IllegalArgumentException if the input is not a well-formed duration token
     */
    public static @NotNull ParsedDuration parse(@NotNull String input) {
        return tryParse(input).orElseThrow(() -> new IllegalArgumentException("Invalid duration format: " + input));
    }

    /**
     * Lenient parse used when the caller cannot know whether the argument is a duration at all — for
     * example the first word after the player name in {@code /ban}, which may just as well be the
     * start of the reason.
     *
     * @return empty when the input is not a duration, so the caller can fall back to treating it as text
     */
    public static @NotNull Optional<ParsedDuration> tryParse(@NotNull String input) {
        String trimmed = input.trim();

        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        if (isPermanentToken(trimmed)) {
            return Optional.of(ParsedDuration.permanent());
        }

        if (!SHAPE.matcher(trimmed).matches()) {
            return Optional.empty();
        }

        return accumulate(trimmed).map(ParsedDuration::of);
    }

    public static boolean isPermanentToken(@NotNull String input) {
        return PERMANENT_TOKENS.contains(input.trim().toLowerCase(Locale.ROOT));
    }

    private static Optional<Duration> accumulate(String token) {
        Matcher matcher = TOKEN.matcher(token);
        long totalSeconds = 0;
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() != lastEnd) {
                return Optional.empty();
            }

            long value = Long.parseLong(matcher.group(1));
            long unit = unitSeconds(matcher.group(2));

            try {
                totalSeconds = Math.addExact(totalSeconds, Math.multiplyExact(value, unit));
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException("Duration is too large: " + token, ex);
            }

            lastEnd = matcher.end();
        }

        if (lastEnd != token.length()) {
            return Optional.empty();
        }

        Duration duration = Duration.ofSeconds(totalSeconds);

        if (duration.isZero()) {
            throw new IllegalArgumentException("Duration must be positive: " + token);
        }

        if (duration.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("Duration exceeds the maximum of " + MAX.toDays() + " days: " + token);
        }

        return Optional.of(duration);
    }

    private static long unitSeconds(String unit) {
        return switch (unit) {
            case "s" -> 1L;
            case "m" -> 60L;
            case "h" -> 3_600L;
            case "d" -> 86_400L;
            case "w" -> 604_800L;
            case "M" -> 2_592_000L;
            case "y" -> 31_536_000L;
            default -> throw new IllegalStateException("Unknown unit: " + unit);
        };
    }

}
