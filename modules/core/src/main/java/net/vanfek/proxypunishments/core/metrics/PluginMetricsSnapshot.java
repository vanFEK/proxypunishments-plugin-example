package net.vanfek.proxypunishments.core.metrics;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public record PluginMetricsSnapshot(
        long cacheHits, long cacheMisses, long staleAnswers,
        long serviceCalls, long serviceFailures,
        long serviceNanos, long slowestNanos,
        boolean invalidationsEnabled, boolean invalidationsSubscribed, long invalidationsReceived
) {

    private static final double PERCENT = 100.0D;

    public long cacheLookups() {
        return this.cacheHits + this.cacheMisses;
    }

    public double hitRatio() {
        long lookups = cacheLookups();

        return lookups == 0L ? 0.0D : this.cacheHits * PERCENT / lookups;
    }

    public @NotNull Duration averageLatency() {
        return this.serviceCalls == 0L ? Duration.ZERO : Duration.ofNanos(this.serviceNanos / this.serviceCalls);
    }

    public @NotNull Duration slowestLatency() {
        return Duration.ofNanos(this.slowestNanos);
    }

}
