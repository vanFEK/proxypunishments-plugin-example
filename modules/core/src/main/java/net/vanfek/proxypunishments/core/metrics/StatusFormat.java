package net.vanfek.proxypunishments.core.metrics;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Locale;

public final class StatusFormat {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private StatusFormat() {
    }

    public static @NotNull String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    public static @NotNull String millis(@NotNull Duration duration) {
        double millis = duration.toNanos() / (double) NANOS_PER_MILLI;

        return String.format(Locale.ROOT, "%.1f ms", millis);
    }

    public static @NotNull String summary(@NotNull PluginMetricsSnapshot snapshot, long cachedEntries) {
        return String.format(
                Locale.ROOT,
                "cache %s hit ratio over %d lookups (%d entries), %d service calls, %d failed, %s average, %s slowest",
                percent(snapshot.hitRatio()), snapshot.cacheLookups(), cachedEntries,
                snapshot.serviceCalls(), snapshot.serviceFailures(),
                millis(snapshot.averageLatency()), millis(snapshot.slowestLatency())
        );
    }

}
