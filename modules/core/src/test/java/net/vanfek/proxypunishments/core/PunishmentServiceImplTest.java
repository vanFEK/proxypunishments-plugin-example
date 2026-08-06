package net.vanfek.proxypunishments.core;

import net.vanfek.proxypunishments.api.ActivePunishmentView;
import net.vanfek.proxypunishments.api.BanType;
import net.vanfek.proxypunishments.api.PunishmentHistoryEntry;
import net.vanfek.proxypunishments.api.PunishmentKind;
import net.vanfek.proxypunishments.core.cache.PunishmentCache;
import net.vanfek.proxypunishments.core.client.PunishmentsClient;
import net.vanfek.proxypunishments.core.client.PunishmentsHttpException;
import net.vanfek.proxypunishments.core.config.PluginConfig;
import net.vanfek.proxypunishments.core.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PunishmentServiceImplTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private PunishmentsClient http;
    private MutableClock clock;
    private PunishmentCache cache;
    private PunishmentServiceImpl service;

    @BeforeEach
    void setUp() {
        this.http = mock(PunishmentsClient.class);
        this.clock = MutableClock.at(Instant.parse("2026-01-01T00:00:00Z"));

        PluginConfig.Cache config = new PluginConfig.Cache();
        config.activeTtl = Duration.ofSeconds(30);
        config.staleTtl = Duration.ofMinutes(30);

        this.cache = new PunishmentCache(config, this.clock);
        this.service = new PunishmentServiceImpl(this.http, this.cache);
    }

    @Test
    @DisplayName("a fresh cached answer is served without touching the network")
    void servesFreshCacheWithoutHttp() throws Exception {
        when(this.http.findActive(PLAYER, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(punishment(PunishmentKind.BAN))));

        this.service.findActive(PLAYER, PunishmentKind.BAN).get();
        this.clock.advance(Duration.ofSeconds(10));
        Optional<ActivePunishmentView> second = this.service.findActive(PLAYER, PunishmentKind.BAN).get();

        assertThat(second).isPresent();
        verify(this.http, times(1)).findActive(PLAYER, PunishmentKind.BAN);
    }

    @Test
    @DisplayName("service down but a recent answer cached: the stale answer keeps the ban enforced")
    void fallsBackToStaleEntryWhenServiceFails() throws Exception {
        when(this.http.findActive(PLAYER, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(punishment(PunishmentKind.BAN))))
                .thenReturn(CompletableFuture.failedFuture(new PunishmentsHttpException("connection refused", null)));

        this.service.findActive(PLAYER, PunishmentKind.BAN).get();
        this.clock.advance(Duration.ofMinutes(5));

        Optional<ActivePunishmentView> stale = this.service.findActive(PLAYER, PunishmentKind.BAN).get();

        assertThat(stale).isPresent();
        assertThat(stale.orElseThrow().reason()).isEqualTo("griefing");
    }

    @Test
    @DisplayName("service down but a recent mute cached: the stale answer keeps the mute enforced")
    void fallsBackToStaleMuteWhenServiceFails() throws Exception {
        when(this.http.findActive(PLAYER, PunishmentKind.MUTE))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(punishment(PunishmentKind.MUTE))))
                .thenReturn(CompletableFuture.failedFuture(new PunishmentsHttpException("connection refused", null)));

        this.service.findActive(PLAYER, PunishmentKind.MUTE).get();
        this.clock.advance(Duration.ofMinutes(5));

        Optional<ActivePunishmentView> stale = this.service.findActive(PLAYER, PunishmentKind.MUTE).get();

        assertThat(stale).isPresent();
        assertThat(stale.orElseThrow().kind()).isEqualTo(PunishmentKind.MUTE);
    }

    @Test
    @DisplayName("a stale mute is no fallback for a ban lookup: the ban failure is propagated")
    void staleFallbackDoesNotCrossKinds() {
        when(this.http.findActive(PLAYER, PunishmentKind.MUTE))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(punishment(PunishmentKind.MUTE))));
        when(this.http.findActive(PLAYER, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.failedFuture(new PunishmentsHttpException("connection refused", null)));

        this.service.findActive(PLAYER, PunishmentKind.MUTE).join();
        this.clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> this.service.findActive(PLAYER, PunishmentKind.BAN).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(PunishmentsHttpException.class);
    }

    @Test
    @DisplayName("service down and nothing usable cached: the failure is propagated so the caller can decide")
    void propagatesFailureWithoutUsableCache() {
        when(this.http.findActive(PLAYER, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.failedFuture(new PunishmentsHttpException("connection refused", null)));

        assertThatThrownBy(() -> this.service.findActive(PLAYER, PunishmentKind.BAN).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(PunishmentsHttpException.class);
    }

    @Test
    @DisplayName("a cached answer older than stale-ttl is not used")
    void doesNotUseExpiredStaleEntry() {
        when(this.http.findActive(PLAYER, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(punishment(PunishmentKind.BAN))))
                .thenReturn(CompletableFuture.failedFuture(new PunishmentsHttpException("connection refused", null)));

        this.service.findActive(PLAYER, PunishmentKind.BAN).join();
        this.clock.advance(Duration.ofMinutes(31));

        assertThatThrownBy(() -> this.service.findActive(PLAYER, PunishmentKind.BAN).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(PunishmentsHttpException.class);
    }

    @Test
    @DisplayName("lifting a ban primes the cache with \"not banned\" so the player can reconnect at once")
    void liftPrimesCacheWithAbsence() throws Exception {
        when(this.http.lift(any(), eq(PunishmentKind.BAN), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        this.service.lift(PLAYER, PunishmentKind.BAN, UUID.randomUUID(), "Admin", "appeal accepted").get();
        Optional<ActivePunishmentView> active = this.service.findActive(PLAYER, PunishmentKind.BAN).get();

        assertThat(active).isEmpty();
        verify(this.http, never()).findActive(PLAYER, PunishmentKind.BAN);
    }

    @Test
    @DisplayName("lifting a mute leaves the cached ban untouched")
    void liftingAMuteDoesNotClearTheBan() throws Exception {
        when(this.http.findActive(PLAYER, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(punishment(PunishmentKind.BAN))));
        when(this.http.lift(any(), eq(PunishmentKind.MUTE), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        this.service.findActive(PLAYER, PunishmentKind.BAN).get();
        this.service.lift(PLAYER, PunishmentKind.MUTE, UUID.randomUUID(), "Admin", "appeal accepted").get();

        assertThat(this.service.findActive(PLAYER, PunishmentKind.BAN).get()).isPresent();
        assertThat(this.service.findActive(PLAYER, PunishmentKind.MUTE).get()).isEmpty();
        verify(this.http, times(1)).findActive(PLAYER, PunishmentKind.BAN);
    }

    @Test
    @DisplayName("issuing a mute primes only the mute cache")
    void issuePrimesTheCacheOfItsOwnKind() throws Exception {
        when(this.http.issue(any(), eq(PunishmentKind.MUTE), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(punishment(PunishmentKind.MUTE)));

        this.service.issue(PLAYER, PunishmentKind.MUTE, UUID.randomUUID(), "Admin", "spam", null).get();

        assertThat(this.service.findActive(PLAYER, PunishmentKind.MUTE).get()).isPresent();
        verify(this.http, never()).findActive(PLAYER, PunishmentKind.MUTE);
        verify(this.http, never()).findActive(PLAYER, PunishmentKind.BAN);
    }

    @Test
    @DisplayName("a warning is log-only: it never becomes an active punishment")
    void warnIsNotCachedAsActive() throws Exception {
        when(this.http.warn(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(warning()));
        when(this.http.findActive(PLAYER, PunishmentKind.BAN)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        PunishmentHistoryEntry entry = this.service.warn(PLAYER, UUID.randomUUID(), "Admin", "spam").get();

        assertThat(entry.action()).isEqualTo(PunishmentHistoryEntry.Action.WARN);
        assertThat(this.service.findActive(PLAYER, PunishmentKind.BAN).get()).isEmpty();
        verify(this.http, times(1)).findActive(PLAYER, PunishmentKind.BAN);
    }

    private ActivePunishmentView punishment(PunishmentKind kind) {
        return new ActivePunishmentView(
                1L, PLAYER, "Steve",
                kind, BanType.PERMANENT,
                UUID.randomUUID(), "Admin",
                "griefing", this.clock.instant(), null
        );
    }

    private PunishmentHistoryEntry warning() {
        return new PunishmentHistoryEntry(
                PLAYER, UUID.randomUUID(), "Admin",
                PunishmentHistoryEntry.Action.WARN, "spam",
                this.clock.instant(), null
        );
    }

}
