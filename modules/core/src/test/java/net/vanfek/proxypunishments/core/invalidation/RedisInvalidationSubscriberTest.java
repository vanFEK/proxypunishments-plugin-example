package net.vanfek.proxypunishments.core.invalidation;

import net.vanfek.proxypunishments.api.ActivePunishmentView;
import net.vanfek.proxypunishments.api.BanType;
import net.vanfek.proxypunishments.api.PunishmentKind;
import net.vanfek.proxypunishments.core.PunishmentServiceImpl;
import net.vanfek.proxypunishments.core.cache.PunishmentCache;
import net.vanfek.proxypunishments.core.client.PunishmentsClient;
import net.vanfek.proxypunishments.core.config.PluginConfig;
import net.vanfek.proxypunishments.core.metrics.PluginMetrics;
import net.vanfek.proxypunishments.core.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class RedisInvalidationSubscriberTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisInvalidationSubscriberTest.class);
    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID ADMIN = UUID.fromString("99999999-8888-7777-6666-555555555555");
    private static final String ADDRESS = "203.0.113.7";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private PunishmentsClient http;
    private PluginMetrics metrics;
    private PluginConfig.Redis config;
    private PunishmentServiceImpl service;
    private RedisInvalidationSubscriber subscriber;

    @BeforeEach
    void setUp() {
        this.http = mock(PunishmentsClient.class);
        this.metrics = new PluginMetrics();

        PluginConfig.Cache cacheConfig = new PluginConfig.Cache();
        cacheConfig.activeTtl = Duration.ofSeconds(30);
        cacheConfig.staleTtl = Duration.ofMinutes(30);

        MutableClock clock = MutableClock.at(NOW);

        this.service = new PunishmentServiceImpl(this.http, new PunishmentCache(cacheConfig, clock), this.metrics);
        this.config = new PluginConfig.Redis();
        this.subscriber = new RedisInvalidationSubscriber(this.config, this.service, this.metrics, LOGGER);
    }

    @Test
    @DisplayName("an event drops the cached ban and leaves that player's mute entry alone")
    void dropsOnlyTheMatchingEntry() throws Exception {
        answerWith(PunishmentKind.BAN);
        answerWith(PunishmentKind.MUTE);

        this.service.findActive(PLAYER, PunishmentKind.BAN).get();
        this.service.findActive(PLAYER, PunishmentKind.MUTE).get();

        this.subscriber.accept(payload(PLAYER.toString(), "BAN"));

        this.service.findActive(PLAYER, PunishmentKind.BAN).get();
        this.service.findActive(PLAYER, PunishmentKind.MUTE).get();

        verify(this.http, times(2)).findActive(PLAYER, PunishmentKind.BAN);
        verify(this.http, times(1)).findActive(PLAYER, PunishmentKind.MUTE);
    }

    @Test
    @DisplayName("an address event drops the address entry and no player entry")
    void dropsOnlyTheAddressEntry() throws Exception {
        answerWith(PunishmentKind.BAN);
        when(this.http.findActiveIpBan(ADDRESS)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        this.service.findActive(PLAYER, PunishmentKind.BAN).get();
        this.service.findActiveIpBan(ADDRESS).get();

        this.subscriber.accept(payload(ADDRESS, "IP_BAN"));

        this.service.findActive(PLAYER, PunishmentKind.BAN).get();
        this.service.findActiveIpBan(ADDRESS).get();

        verify(this.http, times(1)).findActive(PLAYER, PunishmentKind.BAN);
        verify(this.http, times(2)).findActiveIpBan(ADDRESS);
    }

    @Test
    @DisplayName("an event for another player never touches this player's entry")
    void ignoresEventsForOtherTargets() throws Exception {
        answerWith(PunishmentKind.BAN);

        this.service.findActive(PLAYER, PunishmentKind.BAN).get();

        this.subscriber.accept(payload(ADMIN.toString(), "BAN"));

        this.service.findActive(PLAYER, PunishmentKind.BAN).get();

        verify(this.http, times(1)).findActive(PLAYER, PunishmentKind.BAN);
    }

    @Test
    @DisplayName("garbage on the channel is dropped instead of clearing the cache")
    void ignoresMalformedPayloads() throws Exception {
        answerWith(PunishmentKind.BAN);

        this.service.findActive(PLAYER, PunishmentKind.BAN).get();

        this.subscriber.accept("not json at all");
        this.subscriber.accept("{}");
        this.subscriber.accept(payload(PLAYER.toString(), "NONSENSE"));
        this.subscriber.accept(payload("not-a-uuid", "BAN"));

        this.service.findActive(PLAYER, PunishmentKind.BAN).get();

        verify(this.http, times(1)).findActive(PLAYER, PunishmentKind.BAN);
        assertThat(this.metrics.snapshot().invalidationsReceived()).isEqualTo(0L);
    }

    @Test
    void countsAppliedEvents() {
        this.subscriber.accept(payload(PLAYER.toString(), "BAN"));
        this.subscriber.accept(payload(ADDRESS, "IP_BAN"));

        assertThat(this.metrics.snapshot().invalidationsReceived()).isEqualTo(2L);
    }

    @Test
    @DisplayName("with Redis off nothing is started and the plugin reports it as off")
    void disabledStartsNothing() {
        this.config.enabled = false;

        assertThatCode(() -> this.subscriber.start()).doesNotThrowAnyException();

        assertThat(this.metrics.snapshot().invalidationsEnabled()).isFalse();
        assertThat(this.metrics.snapshot().invalidationsSubscribed()).isFalse();

        assertThatCode(() -> this.subscriber.close()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an unreachable Redis is retried in the background and never reaches a punishment lookup")
    void anUnreachableRedisDoesNotBreakLookups() throws Exception {
        this.config.enabled = true;
        this.config.host = "127.0.0.1";
        this.config.port = closedPort();
        this.config.connectTimeout = Duration.ofMillis(200L);
        this.config.reconnectDelay = Duration.ofMillis(50L);

        answerWith(PunishmentKind.BAN);

        this.subscriber.start();

        Thread.sleep(300L);

        assertThat(this.service.findActive(PLAYER, PunishmentKind.BAN).get()).isPresent();
        assertThat(this.metrics.snapshot().invalidationsEnabled()).isTrue();
        assertThat(this.metrics.snapshot().invalidationsSubscribed()).isFalse();

        assertThatCode(() -> this.subscriber.close()).doesNotThrowAnyException();
    }

    private void answerWith(PunishmentKind kind) {
        when(this.http.findActive(PLAYER, kind))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(punishment(kind))));
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String payload(String target, String kind) {
        return "{\"target\":\"" + target + "\",\"kind\":\"" + kind + "\",\"origin\":\"service-1\"}";
    }

    private static ActivePunishmentView punishment(PunishmentKind kind) {
        return new ActivePunishmentView(
                1L, PLAYER, "Steve",
                kind, BanType.PERMANENT, null,
                ADMIN, "Admin",
                "griefing", NOW, null
        );
    }

}
