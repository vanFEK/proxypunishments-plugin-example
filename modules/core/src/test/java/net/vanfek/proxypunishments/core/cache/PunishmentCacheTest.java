package net.vanfek.proxypunishments.core.cache;

import net.vanfek.proxypunishments.api.ActivePunishmentView;
import net.vanfek.proxypunishments.api.BanType;
import net.vanfek.proxypunishments.api.PunishmentKind;
import net.vanfek.proxypunishments.core.config.PluginConfig;
import net.vanfek.proxypunishments.core.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PunishmentCacheTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private MutableClock clock;
    private PunishmentCache cache;

    @BeforeEach
    void setUp() {
        this.clock = MutableClock.at(Instant.parse("2026-01-01T00:00:00Z"));

        PluginConfig.Cache config = new PluginConfig.Cache();
        config.activeTtl = Duration.ofSeconds(30);
        config.staleTtl = Duration.ofMinutes(30);

        this.cache = new PunishmentCache(config, this.clock);
    }

    @Test
    void missingEntryIsEmpty() {
        assertThat(this.cache.peek(PLAYER, PunishmentKind.BAN)).isEmpty();
    }

    @Test
    @DisplayName("\"not banned\" is cached too, otherwise every clean login would hit the service")
    void cachesAbsence() {
        this.cache.store(PLAYER, PunishmentKind.BAN, Optional.empty());

        Optional<CachedPunishment> entry = this.cache.peek(PLAYER, PunishmentKind.BAN);

        assertThat(entry).isPresent();
        assertThat(entry.orElseThrow().asOptional()).isEmpty();
        assertThat(this.cache.isFresh(entry.orElseThrow())).isTrue();
    }

    @Test
    void entryIsFreshInsideActiveTtl() {
        this.cache.store(PLAYER, PunishmentKind.BAN, Optional.of(punishment(PunishmentKind.BAN)));

        this.clock.advance(Duration.ofSeconds(29));

        assertThat(this.cache.isFresh(this.cache.peek(PLAYER, PunishmentKind.BAN).orElseThrow())).isTrue();
    }

    @Test
    @DisplayName("past active-ttl the entry stops being fresh but stays usable as a stale fallback")
    void entryBecomesStaleButUsable() {
        this.cache.store(PLAYER, PunishmentKind.BAN, Optional.of(punishment(PunishmentKind.BAN)));

        this.clock.advance(Duration.ofMinutes(5));

        CachedPunishment entry = this.cache.peek(PLAYER, PunishmentKind.BAN).orElseThrow();

        assertThat(this.cache.isFresh(entry)).isFalse();
        assertThat(this.cache.isUsableStale(entry)).isTrue();
    }

    @Test
    void entryStopsBeingUsableAfterStaleTtl() {
        this.cache.store(PLAYER, PunishmentKind.BAN, Optional.of(punishment(PunishmentKind.BAN)));

        this.clock.advance(Duration.ofMinutes(31));

        CachedPunishment entry = this.cache.peek(PLAYER, PunishmentKind.BAN).orElseThrow();

        assertThat(this.cache.isUsableStale(entry)).isFalse();
    }

    @Test
    void invalidateDropsTheEntry() {
        this.cache.store(PLAYER, PunishmentKind.BAN, Optional.of(punishment(PunishmentKind.BAN)));
        this.cache.invalidate(PLAYER);

        assertThat(this.cache.peek(PLAYER, PunishmentKind.BAN)).isEmpty();
    }

    @Test
    @DisplayName("a cached mute must never answer a ban lookup")
    void aMuteDoesNotAnswerABanLookup() {
        this.cache.store(PLAYER, PunishmentKind.MUTE, Optional.of(punishment(PunishmentKind.MUTE)));

        assertThat(this.cache.peek(PLAYER, PunishmentKind.BAN)).isEmpty();
        assertThat(this.cache.peek(PLAYER, PunishmentKind.MUTE)).isPresent();
    }

    @Test
    @DisplayName("\"not muted\" cached for a player says nothing about that player's ban")
    void cachedAbsenceIsPerKind() {
        this.cache.store(PLAYER, PunishmentKind.MUTE, Optional.empty());
        this.cache.store(PLAYER, PunishmentKind.BAN, Optional.of(punishment(PunishmentKind.BAN)));

        assertThat(this.cache.peek(PLAYER, PunishmentKind.MUTE).orElseThrow().asOptional()).isEmpty();
        assertThat(this.cache.peek(PLAYER, PunishmentKind.BAN).orElseThrow().asOptional())
                .get()
                .extracting(ActivePunishmentView::kind)
                .isEqualTo(PunishmentKind.BAN);
    }

    @Test
    void invalidatingOneKindLeavesTheOtherInPlace() {
        this.cache.store(PLAYER, PunishmentKind.BAN, Optional.of(punishment(PunishmentKind.BAN)));
        this.cache.store(PLAYER, PunishmentKind.MUTE, Optional.of(punishment(PunishmentKind.MUTE)));

        this.cache.invalidate(PLAYER, PunishmentKind.MUTE);

        assertThat(this.cache.peek(PLAYER, PunishmentKind.MUTE)).isEmpty();
        assertThat(this.cache.peek(PLAYER, PunishmentKind.BAN)).isPresent();
    }

    @Test
    void invalidatingAPlayerDropsEveryKind() {
        this.cache.store(PLAYER, PunishmentKind.BAN, Optional.of(punishment(PunishmentKind.BAN)));
        this.cache.store(PLAYER, PunishmentKind.MUTE, Optional.of(punishment(PunishmentKind.MUTE)));

        this.cache.invalidate(PLAYER);

        assertThat(this.cache.peek(PLAYER, PunishmentKind.BAN)).isEmpty();
        assertThat(this.cache.peek(PLAYER, PunishmentKind.MUTE)).isEmpty();
    }

    private ActivePunishmentView punishment(PunishmentKind kind) {
        return new ActivePunishmentView(
                1L, PLAYER, "Steve",
                kind, BanType.PERMANENT,
                UUID.randomUUID(), "Admin",
                "griefing", this.clock.instant(), null
        );
    }

}
