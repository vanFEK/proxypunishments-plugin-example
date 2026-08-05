package net.vanfek.proxybans.core;

import net.vanfek.proxybans.api.ActiveBanView;
import net.vanfek.proxybans.api.BanType;
import net.vanfek.proxybans.core.cache.BanCache;
import net.vanfek.proxybans.core.client.BansClient;
import net.vanfek.proxybans.core.client.BansHttpException;
import net.vanfek.proxybans.core.config.PluginConfig;
import net.vanfek.proxybans.core.support.MutableClock;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BanServiceImplTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private BansClient http;
    private MutableClock clock;
    private BanCache cache;
    private BanServiceImpl service;

    @BeforeEach
    void setUp() {
        this.http = mock(BansClient.class);
        this.clock = MutableClock.at(Instant.parse("2026-01-01T00:00:00Z"));

        PluginConfig.Cache config = new PluginConfig.Cache();
        config.activeTtl = Duration.ofSeconds(30);
        config.staleTtl = Duration.ofMinutes(30);

        this.cache = new BanCache(config, this.clock);
        this.service = new BanServiceImpl(this.http, this.cache);
    }

    @Test
    @DisplayName("a fresh cached answer is served without touching the network")
    void servesFreshCacheWithoutHttp() throws Exception {
        when(this.http.findActiveBan(PLAYER)).thenReturn(CompletableFuture.completedFuture(Optional.of(ban())));

        this.service.findActiveBan(PLAYER).get();
        this.clock.advance(Duration.ofSeconds(10));
        Optional<ActiveBanView> second = this.service.findActiveBan(PLAYER).get();

        assertThat(second).isPresent();
        verify(this.http, times(1)).findActiveBan(PLAYER);
    }

    @Test
    @DisplayName("service down but a recent answer cached: the stale answer keeps the ban enforced")
    void fallsBackToStaleEntryWhenServiceFails() throws Exception {
        when(this.http.findActiveBan(PLAYER))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(ban())))
                .thenReturn(CompletableFuture.failedFuture(new BansHttpException("connection refused", null)));

        this.service.findActiveBan(PLAYER).get();
        this.clock.advance(Duration.ofMinutes(5));

        Optional<ActiveBanView> stale = this.service.findActiveBan(PLAYER).get();

        assertThat(stale).isPresent();
        assertThat(stale.orElseThrow().reason()).isEqualTo("griefing");
    }

    @Test
    @DisplayName("service down and nothing usable cached: the failure is propagated so the caller can decide")
    void propagatesFailureWithoutUsableCache() {
        when(this.http.findActiveBan(PLAYER))
                .thenReturn(CompletableFuture.failedFuture(new BansHttpException("connection refused", null)));

        assertThatThrownBy(() -> this.service.findActiveBan(PLAYER).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(BansHttpException.class);
    }

    @Test
    @DisplayName("a cached answer older than stale-ttl is not used")
    void doesNotUseExpiredStaleEntry() {
        when(this.http.findActiveBan(PLAYER))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(ban())))
                .thenReturn(CompletableFuture.failedFuture(new BansHttpException("connection refused", null)));

        this.service.findActiveBan(PLAYER).join();
        this.clock.advance(Duration.ofMinutes(31));

        assertThatThrownBy(() -> this.service.findActiveBan(PLAYER).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(BansHttpException.class);
    }

    @Test
    @DisplayName("lifting a ban primes the cache with \"not banned\" so the player can reconnect at once")
    void liftPrimesCacheWithAbsence() throws Exception {
        when(this.http.liftBan(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        this.service.liftBan(PLAYER, UUID.randomUUID(), "Admin", "appeal accepted").get();
        Optional<ActiveBanView> active = this.service.findActiveBan(PLAYER).get();

        assertThat(active).isEmpty();
        verify(this.http, never()).findActiveBan(PLAYER);
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
