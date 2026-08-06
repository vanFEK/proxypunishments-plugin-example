package net.vanfek.proxypunishments.core.metrics;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** A proxy has nowhere to scrape from, so the numbers live here and come out through {@code /pp status} and the log. */
public final class PluginMetrics {

    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong staleAnswers = new AtomicLong();

    private final AtomicLong serviceCalls = new AtomicLong();
    private final AtomicLong serviceFailures = new AtomicLong();
    private final AtomicLong serviceNanos = new AtomicLong();
    private final AtomicLong slowestNanos = new AtomicLong();

    private final AtomicLong invalidationsReceived = new AtomicLong();
    private final AtomicBoolean invalidationsSubscribed = new AtomicBoolean();
    private final AtomicBoolean invalidationsEnabled = new AtomicBoolean();

    public void cacheHit() {
        this.cacheHits.incrementAndGet();
    }

    public void cacheMiss() {
        this.cacheMisses.incrementAndGet();
    }

    public void staleAnswer() {
        this.staleAnswers.incrementAndGet();
    }

    public void serviceCall(long nanos, boolean failed) {
        this.serviceCalls.incrementAndGet();
        this.serviceNanos.addAndGet(nanos);
        this.slowestNanos.accumulateAndGet(nanos, Math::max);

        if (failed) {
            this.serviceFailures.incrementAndGet();
        }
    }

    public void invalidationReceived() {
        this.invalidationsReceived.incrementAndGet();
    }

    public void invalidationsEnabled(boolean enabled) {
        this.invalidationsEnabled.set(enabled);
    }

    public void invalidationsSubscribed(boolean subscribed) {
        this.invalidationsSubscribed.set(subscribed);
    }

    public @NotNull PluginMetricsSnapshot snapshot() {
        return new PluginMetricsSnapshot(
                this.cacheHits.get(), this.cacheMisses.get(), this.staleAnswers.get(),
                this.serviceCalls.get(), this.serviceFailures.get(),
                this.serviceNanos.get(), this.slowestNanos.get(),
                this.invalidationsEnabled.get(), this.invalidationsSubscribed.get(), this.invalidationsReceived.get()
        );
    }

}
