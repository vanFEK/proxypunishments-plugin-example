package net.vanfek.proxypunishments.velocity.listener;

import com.velocitypowered.api.event.ResultedEvent.ComponentResult;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.vanfek.proxypunishments.api.ActivePunishmentView;
import net.vanfek.proxypunishments.api.BanType;
import net.vanfek.proxypunishments.api.PunishmentKind;
import net.vanfek.proxypunishments.core.PunishmentServiceImpl;
import net.vanfek.proxypunishments.core.cache.PunishmentCache;
import net.vanfek.proxypunishments.core.client.PunishmentsClient;
import net.vanfek.proxypunishments.core.client.PunishmentsHttpException;
import net.vanfek.proxypunishments.core.config.PluginConfig;
import net.vanfek.proxypunishments.core.config.RuntimeConfiguration;
import net.vanfek.proxypunishments.core.metrics.PluginMetrics;
import net.vanfek.proxypunishments.core.i18n.MessageBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginListenerTest {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String ADDRESS = "203.0.113.7";
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginListenerTest.class);

    private PunishmentsClient client;
    private PunishmentServiceImpl punishments;
    private MessageBundle messages;
    private PluginConfig config;
    private RuntimeConfiguration runtime;
    private Player player;
    private LoginEvent event;

    @BeforeEach
    void setUp(@TempDir Path directory) throws IOException {
        this.client = mock(PunishmentsClient.class);

        PluginConfig.Cache cacheConfig = new PluginConfig.Cache();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        this.punishments = new PunishmentServiceImpl(
                this.client, new PunishmentCache(cacheConfig, clock),
                new PluginMetrics()
        );
        this.messages = MessageBundle.load(directory);
        this.config = new PluginConfig();
        this.runtime = new RuntimeConfiguration(this.config, this.messages);

        this.player = mock(Player.class);
        when(this.player.getUniqueId()).thenReturn(PLAYER_UUID);
        when(this.player.getUsername()).thenReturn("Steve");
        when(this.player.getRemoteAddress()).thenReturn(new InetSocketAddress(ADDRESS, 25565));

        this.event = mock(LoginEvent.class);
        when(this.event.getPlayer()).thenReturn(this.player);

        when(this.client.rememberPlayer(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(this.client.findActiveIpBan(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    }

    @Test
    void deniesLoginForAnActiveBan() {
        when(this.client.findActive(PLAYER_UUID, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(permanentBan())));

        listener(Clock.fixed(NOW, ZoneOffset.UTC)).handle(this.event).join();

        verify(this.event).setResult(any(ComponentResult.class));
    }

    @Test
    @DisplayName("a ban whose expiry has passed no longer blocks the login")
    void allowsLoginWhenTheBanHasExpired() {
        ActivePunishmentView expired = new ActivePunishmentView(
                1L, PLAYER_UUID, "Steve",
                PunishmentKind.BAN, BanType.TEMPORARY, null,
                UUID.randomUUID(), "Admin",
                "griefing", NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(1))
        );

        when(this.client.findActive(PLAYER_UUID, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(expired)));

        listener(Clock.fixed(NOW, ZoneOffset.UTC)).handle(this.event).join();

        verify(this.event, never()).setResult(any());
    }

    @Test
    @DisplayName("an active mute never blocks the login")
    void ignoresMutesOnLogin() {
        when(this.client.findActive(PLAYER_UUID, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        listener(Clock.fixed(NOW, ZoneOffset.UTC)).handle(this.event).join();

        verify(this.client, never()).findActive(PLAYER_UUID, PunishmentKind.MUTE);
        verify(this.event, never()).setResult(any());
    }

    @Test
    @DisplayName("fail-open: the service is down, the player gets in and the incident is logged")
    void allowsLoginWhenServiceIsDownAndFailOpenIsOn() {
        this.config.login.failOpen = true;

        when(this.client.findActive(PLAYER_UUID, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.failedFuture(new PunishmentsHttpException("connection refused", null)));

        listener(Clock.fixed(NOW, ZoneOffset.UTC)).handle(this.event).join();

        verify(this.event, never()).setResult(any());
    }

    @Test
    @DisplayName("fail-closed: the service is down and the login is rejected with an explanation")
    void deniesLoginWhenServiceIsDownAndFailOpenIsOff() {
        this.config.login.failOpen = false;

        when(this.client.findActive(PLAYER_UUID, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.failedFuture(new PunishmentsHttpException("connection refused", null)));

        listener(Clock.fixed(NOW, ZoneOffset.UTC)).handle(this.event).join();

        verify(this.event).setResult(any(ComponentResult.class));
    }

    @Test
    @DisplayName("a banned player is not reported to the known-players endpoint")
    void doesNotRememberDeniedPlayers() {
        when(this.client.findActive(PLAYER_UUID, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(permanentBan())));

        listener(Clock.fixed(NOW, ZoneOffset.UTC)).handle(this.event).join();

        verify(this.client, never()).rememberPlayer(any(), any(), any());
    }

    @Test
    @DisplayName("the connecting address is reported alongside the name")
    void remembersAllowedPlayersWhenEnabled() {
        this.config.login.rememberPlayer = true;

        when(this.client.findActive(PLAYER_UUID, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        listener(Clock.fixed(NOW, ZoneOffset.UTC)).handle(this.event).join();

        verify(this.client).rememberPlayer(PLAYER_UUID, "Steve", ADDRESS);
    }

    @Test
    @DisplayName("a failure while remembering a player never blocks the login")
    void ignoresRememberFailures() {
        this.config.login.rememberPlayer = true;

        when(this.client.findActive(PLAYER_UUID, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(this.client.rememberPlayer(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new PunishmentsHttpException("boom", null)));

        listener(Clock.fixed(NOW, ZoneOffset.UTC)).handle(this.event).join();

        verify(this.event, never()).setResult(any());
    }

    @Test
    @DisplayName("stale cache: the service dies after one good answer and the ban is still enforced")
    void keepsEnforcingBanFromStaleCache() {
        when(this.client.findActive(PLAYER_UUID, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(permanentBan())))
                .thenReturn(CompletableFuture.failedFuture(new PunishmentsHttpException("connection refused", null)));

        PluginConfig.Cache cacheConfig = new PluginConfig.Cache();
        MovingClock clock = new MovingClock(NOW);
        PunishmentServiceImpl service = new PunishmentServiceImpl(
                this.client, new PunishmentCache(cacheConfig, clock),
                new PluginMetrics()
        );

        LoginListener listener = new LoginListener(service, this.runtime, clock, LOGGER);

        listener.handle(this.event).join();
        clock.advance(Duration.ofMinutes(10));
        listener.handle(this.event).join();

        verify(this.event, times(2)).setResult(any(ComponentResult.class));
    }

    @Test
    @DisplayName("an IP ban denies the login of a player who is not banned themselves")
    void deniesLoginForAnIpBanWhenThePlayerIsClean() {
        when(this.client.findActive(PLAYER_UUID, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(this.client.findActiveIpBan(ADDRESS))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(permanentIpBan())));

        listener(Clock.fixed(NOW, ZoneOffset.UTC)).handle(this.event).join();

        verify(this.event).setResult(any(ComponentResult.class));
        verify(this.client, never()).rememberPlayer(any(), any(), any());
    }

    @Test
    @DisplayName("the player ban and the IP ban produce different kick screens")
    void theTwoDenialsUseDistinctMessages() {
        when(this.client.findActive(PLAYER_UUID, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(permanentBan())));

        listener(Clock.fixed(NOW, ZoneOffset.UTC)).handle(this.event).join();

        assertThat(this.messages.raw("login.kick-permanent"))
                .isNotEqualTo(this.messages.raw("login.kick-ip-permanent"));
        verify(this.client, never()).findActiveIpBan(any());
    }

    @Test
    @DisplayName("an expired IP ban lets the player through")
    void allowsLoginWhenTheIpBanHasExpired() {
        ActivePunishmentView expired = new ActivePunishmentView(
                2L, PLAYER_UUID, "Steve",
                PunishmentKind.IP_BAN, BanType.TEMPORARY, ADDRESS,
                UUID.randomUUID(), "Admin",
                "ban evasion", NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(1))
        );

        when(this.client.findActive(PLAYER_UUID, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(this.client.findActiveIpBan(ADDRESS)).thenReturn(CompletableFuture.completedFuture(Optional.of(expired)));

        listener(Clock.fixed(NOW, ZoneOffset.UTC)).handle(this.event).join();

        verify(this.event, never()).setResult(any());
    }

    @Test
    @DisplayName("fail-open still applies when it is the IP lookup that fails")
    void allowsLoginWhenTheIpLookupFailsAndFailOpenIsOn() {
        this.config.login.failOpen = true;

        when(this.client.findActive(PLAYER_UUID, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(this.client.findActiveIpBan(ADDRESS))
                .thenReturn(CompletableFuture.failedFuture(new PunishmentsHttpException("connection refused", null)));

        listener(Clock.fixed(NOW, ZoneOffset.UTC)).handle(this.event).join();

        verify(this.event, never()).setResult(any());
    }

    @Test
    @DisplayName("stale cache: the IP ban stays enforced while the service is unreachable")
    void keepsEnforcingIpBanFromStaleCache() {
        when(this.client.findActive(PLAYER_UUID, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(this.client.findActiveIpBan(ADDRESS))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(permanentIpBan())))
                .thenReturn(CompletableFuture.failedFuture(new PunishmentsHttpException("connection refused", null)));

        PluginConfig.Cache cacheConfig = new PluginConfig.Cache();
        MovingClock clock = new MovingClock(NOW);
        PunishmentServiceImpl service = new PunishmentServiceImpl(
                this.client, new PunishmentCache(cacheConfig, clock),
                new PluginMetrics()
        );

        LoginListener listener = new LoginListener(service, this.runtime, clock, LOGGER);

        listener.handle(this.event).join();
        clock.advance(Duration.ofMinutes(10));
        listener.handle(this.event).join();

        verify(this.event, times(2)).setResult(any(ComponentResult.class));
    }

    private LoginListener listener(Clock clock) {
        return new LoginListener(this.punishments, this.runtime, clock, LOGGER);
    }

    private ActivePunishmentView permanentBan() {
        return new ActivePunishmentView(
                1L, PLAYER_UUID, "Steve",
                PunishmentKind.BAN, BanType.PERMANENT, null,
                UUID.randomUUID(), "Admin",
                "griefing", NOW.minus(Duration.ofDays(1)), null
        );
    }

    private ActivePunishmentView permanentIpBan() {
        return new ActivePunishmentView(
                2L, PLAYER_UUID, "Steve",
                PunishmentKind.IP_BAN, BanType.PERMANENT, ADDRESS,
                UUID.randomUUID(), "Admin",
                "ban evasion", NOW.minus(Duration.ofDays(1)), null
        );
    }

    private static final class MovingClock extends Clock {

        private Instant now;

        private MovingClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration amount) {
            this.now = this.now.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.now;
        }

    }

}
