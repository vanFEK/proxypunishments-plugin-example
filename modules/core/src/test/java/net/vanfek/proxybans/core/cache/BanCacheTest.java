package net.vanfek.proxybans.core.cache;

import net.vanfek.proxybans.api.ActiveBanView;
import net.vanfek.proxybans.api.BanType;
import net.vanfek.proxybans.core.config.PluginConfig;
import net.vanfek.proxybans.core.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BanCacheTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private MutableClock clock;
    private BanCache cache;

    @BeforeEach
    void setUp() {
        this.clock = MutableClock.at(Instant.parse("2026-01-01T00:00:00Z"));

        PluginConfig.Cache config = new PluginConfig.Cache();
        config.activeTtl = Duration.ofSeconds(30);
        config.staleTtl = Duration.ofMinutes(30);

        this.cache = new BanCache(config, this.clock);
    }

    @Test
    void missingEntryIsEmpty() {
        assertThat(this.cache.peek(PLAYER)).isEmpty();
    }

    @Test
    @DisplayName("\"not banned\" is cached too, otherwise every clean login would hit the service")
    void cachesAbsence() {
        this.cache.store(PLAYER, Optional.empty());

        Optional<CachedBan> entry = this.cache.peek(PLAYER);

        assertThat(entry).isPresent();
        assertThat(entry.orElseThrow().asOptional()).isEmpty();
        assertThat(this.cache.isFresh(entry.orElseThrow())).isTrue();
    }

    @Test
    void entryIsFreshInsideActiveTtl() {
        this.cache.store(PLAYER, Optional.of(ban()));

        this.clock.advance(Duration.ofSeconds(29));

        assertThat(this.cache.isFresh(this.cache.peek(PLAYER).orElseThrow())).isTrue();
    }

    @Test
    @DisplayName("past active-ttl the entry stops being fresh but stays usable as a stale fallback")
    void entryBecomesStaleButUsable() {
        this.cache.store(PLAYER, Optional.of(ban()));

        this.clock.advance(Duration.ofMinutes(5));

        CachedBan entry = this.cache.peek(PLAYER).orElseThrow();

        assertThat(this.cache.isFresh(entry)).isFalse();
        assertThat(this.cache.isUsableStale(entry)).isTrue();
    }

    @Test
    void entryStopsBeingUsableAfterStaleTtl() {
        this.cache.store(PLAYER, Optional.of(ban()));

        this.clock.advance(Duration.ofMinutes(31));

        CachedBan entry = this.cache.peek(PLAYER).orElseThrow();

        assertThat(this.cache.isUsableStale(entry)).isFalse();
    }

    @Test
    void invalidateDropsTheEntry() {
        this.cache.store(PLAYER, Optional.of(ban()));
        this.cache.invalidate(PLAYER);

        assertThat(this.cache.peek(PLAYER)).isEmpty();
    }

    private ActiveBanView ban() {
        return new ActiveBanView(
                1L,
                PLAYER,
                "Steve",
                UUID.randomUUID(),
                "Admin",
                "griefing",
                this.clock.instant(),
                null,
                BanType.PERMANENT
        );
    }

}
