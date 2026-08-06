package net.vanfek.proxybans.velocity.listener;

import com.velocitypowered.api.event.ResultedEvent.ComponentResult;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.vanfek.proxybans.api.ActivePunishmentView;
import net.vanfek.proxybans.api.BanType;
import net.vanfek.proxybans.api.PunishmentKind;
import net.vanfek.proxybans.core.PunishmentServiceImpl;
import net.vanfek.proxybans.core.cache.PunishmentCache;
import net.vanfek.proxybans.core.client.PunishmentsClient;
import net.vanfek.proxybans.core.client.PunishmentsHttpException;
import net.vanfek.proxybans.core.config.PluginConfig;
import net.vanfek.proxybans.core.i18n.MessageBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginListenerTest {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginListenerTest.class);

    private PunishmentsClient client;
    private PunishmentServiceImpl punishments;
    private MessageBundle messages;
    private PluginConfig.Login config;
    private Player player;
    private LoginEvent event;

    @BeforeEach
    void setUp(@TempDir Path directory) throws IOException {
        this.client = mock(PunishmentsClient.class);

        PluginConfig.Cache cacheConfig = new PluginConfig.Cache();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        this.punishments = new PunishmentServiceImpl(this.client, new PunishmentCache(cacheConfig, clock));
        this.messages = MessageBundle.load(directory);
        this.config = new PluginConfig.Login();

        this.player = mock(Player.class);
        when(this.player.getUniqueId()).thenReturn(PLAYER_UUID);
        when(this.player.getUsername()).thenReturn("Steve");

        this.event = mock(LoginEvent.class);
        when(this.event.getPlayer()).thenReturn(this.player);

        when(this.client.rememberPlayer(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
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
                PunishmentKind.BAN, BanType.TEMPORARY,
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
        this.config.failOpen = true;

        when(this.client.findActive(PLAYER_UUID, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.failedFuture(new PunishmentsHttpException("connection refused", null)));

        listener(Clock.fixed(NOW, ZoneOffset.UTC)).handle(this.event).join();

        verify(this.event, never()).setResult(any());
    }

    @Test
    @DisplayName("fail-closed: the service is down and the login is rejected with an explanation")
    void deniesLoginWhenServiceIsDownAndFailOpenIsOff() {
        this.config.failOpen = false;

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

        verify(this.client, never()).rememberPlayer(any(), any());
    }

    @Test
    void remembersAllowedPlayersWhenEnabled() {
        this.config.rememberPlayer = true;

        when(this.client.findActive(PLAYER_UUID, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        listener(Clock.fixed(NOW, ZoneOffset.UTC)).handle(this.event).join();

        verify(this.client).rememberPlayer(PLAYER_UUID, "Steve");
    }

    @Test
    @DisplayName("a failure while remembering a player never blocks the login")
    void ignoresRememberFailures() {
        this.config.rememberPlayer = true;

        when(this.client.findActive(PLAYER_UUID, PunishmentKind.BAN))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(this.client.rememberPlayer(any(), any()))
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
        PunishmentServiceImpl service = new PunishmentServiceImpl(this.client, new PunishmentCache(cacheConfig, clock));

        LoginListener listener = new LoginListener(service, this.messages, this.config, clock, LOGGER);

        listener.handle(this.event).join();
        clock.advance(Duration.ofMinutes(10));
        listener.handle(this.event).join();

        verify(this.event, times(2)).setResult(any(ComponentResult.class));
    }

    private LoginListener listener(Clock clock) {
        return new LoginListener(this.punishments, this.messages, this.config, clock, LOGGER);
    }

    private ActivePunishmentView permanentBan() {
        return new ActivePunishmentView(
                1L, PLAYER_UUID, "Steve",
                PunishmentKind.BAN, BanType.PERMANENT,
                UUID.randomUUID(), "Admin",
                "griefing", NOW.minus(Duration.ofDays(1)), null
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
