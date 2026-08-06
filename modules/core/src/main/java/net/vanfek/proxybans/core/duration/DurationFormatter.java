package net.vanfek.proxybans.core.duration;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public final class DurationFormatter {

    private static final long SECONDS_PER_DAY = 86_400L;
    private static final long SECONDS_PER_HOUR = 3_600L;
    private static final long SECONDS_PER_MINUTE = 60L;

    private DurationFormatter() {
    }

    public static @NotNull String human(@NotNull Duration duration) {
        long seconds = Math.max(0L, duration.toSeconds());

        long days = seconds / SECONDS_PER_DAY;
        long hours = (seconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR;
        long minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE;
        long remainingSeconds = seconds % SECONDS_PER_MINUTE;

        StringBuilder builder = new StringBuilder();

        if (days > 0) {
            builder.append(days).append("д ");
        }

        if (hours > 0) {
            builder.append(hours).append("ч ");
        }

        if (minutes > 0) {
            builder.append(minutes).append("м ");
        }

        if (remainingSeconds > 0 || builder.isEmpty()) {
            builder.append(remainingSeconds).append("с");
        }

        return builder.toString().trim();
    }

}
