package net.vanfek.proxybans.core.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Test clock that only moves when a test says so. Lets TTL behaviour be asserted exactly instead of
 * being approximated with sleeps.
 */
public final class MutableClock extends Clock {

    private final ZoneId zone;

    private Instant now;

    private MutableClock(Instant now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    public static MutableClock at(Instant now) {
        return new MutableClock(now, ZoneId.of("UTC"));
    }

    public void advance(Duration amount) {
        this.now = this.now.plus(amount);
    }

    @Override
    public ZoneId getZone() {
        return this.zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(this.now, zone);
    }

    @Override
    public Instant instant() {
        return this.now;
    }

}
