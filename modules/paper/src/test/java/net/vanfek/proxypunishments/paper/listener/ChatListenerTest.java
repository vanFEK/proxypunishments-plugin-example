package net.vanfek.proxypunishments.paper.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.vanfek.proxypunishments.api.ActivePunishmentView;
import net.vanfek.proxypunishments.api.BanType;
import net.vanfek.proxypunishments.api.PunishmentKind;
import net.vanfek.proxypunishments.core.PunishmentServiceImpl;
import net.vanfek.proxypunishments.core.cache.PunishmentCache;
import net.vanfek.proxypunishments.core.client.PunishmentsClient;
import net.vanfek.proxypunishments.core.client.PunishmentsHttpException;
import net.vanfek.proxypunishments.core.config.PluginConfig;
import net.vanfek.proxypunishments.core.i18n.MessageBundle;
import org.bukkit.entity.Player;
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
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatListenerTest {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatListenerTest.class);

    private PunishmentsClient client;
    private PunishmentServiceImpl punishments;
    private MessageBundle messages;
    private PluginConfig.Chat config;
    private Player player;

    @BeforeEach
    void setUp(@TempDir Path directory) throws IOException {
        this.client = mock(PunishmentsClient.class);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        this.punishments = new PunishmentServiceImpl(this.client, new PunishmentCache(new PluginConfig.Cache(), clock));
        this.messages = MessageBundle.load(directory);
        this.config = new PluginConfig.Chat();

        this.player = mock(Player.class);
        when(this.player.getUniqueId()).thenReturn(PLAYER_UUID);
        when(this.player.getName()).thenReturn("Steve");
    }

    @Test
    @DisplayName("an active mute cancels the message and tells the player why")
    void cancelsChatForAnActiveMute() {
        when(this.client.findActive(PLAYER_UUID, PunishmentKind.MUTE))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(permanentMute())));

        AsyncChatEvent event = chatEvent();

        listener().onChat(event);

        assertThat(event.isCancelled()).isTrue();
        verify(this.player).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("a mute whose expiry has passed no longer blocks chat")
    void allowsChatWhenTheMuteHasExpired() {
        ActivePunishmentView expired = new ActivePunishmentView(
                1L, PLAYER_UUID, "Steve",
                PunishmentKind.MUTE, BanType.TEMPORARY,
                UUID.randomUUID(), "Admin",
                "spam", NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(1))
        );

        when(this.client.findActive(PLAYER_UUID, PunishmentKind.MUTE))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(expired)));

        AsyncChatEvent event = chatEvent();

        listener().onChat(event);

        assertThat(event.isCancelled()).isFalse();
        verify(this.player, never()).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("an unmuted player is left alone")
    void allowsChatWithoutAMute() {
        when(this.client.findActive(PLAYER_UUID, PunishmentKind.MUTE))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        AsyncChatEvent event = chatEvent();

        listener().onChat(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("fail-open: the service is down and the message goes through")
    void allowsChatWhenServiceIsDownAndFailOpenIsOn() {
        this.config.failOpen = true;

        when(this.client.findActive(PLAYER_UUID, PunishmentKind.MUTE))
                .thenReturn(CompletableFuture.failedFuture(new PunishmentsHttpException("connection refused", null)));

        AsyncChatEvent event = chatEvent();

        listener().onChat(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("fail-closed: the service is down and the message is held back with an explanation")
    void cancelsChatWhenServiceIsDownAndFailOpenIsOff() {
        this.config.failOpen = false;

        when(this.client.findActive(PLAYER_UUID, PunishmentKind.MUTE))
                .thenReturn(CompletableFuture.failedFuture(new PunishmentsHttpException("connection refused", null)));

        AsyncChatEvent event = chatEvent();

        listener().onChat(event);

        assertThat(event.isCancelled()).isTrue();
        verify(this.player).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("only the configured commands count as chat, with any namespace stripped")
    void recognisesTheConfiguredChatCommands() {
        ChatListener listener = listener();

        assertThat(listener.isBlockedCommand("/msg Steve hello")).isTrue();
        assertThat(listener.isBlockedCommand("/essentials:msg Steve hello")).isTrue();
        assertThat(listener.isBlockedCommand("/MSG Steve hello")).isTrue();
        assertThat(listener.isBlockedCommand("/home")).isFalse();
        assertThat(listener.isBlockedCommand("/message Steve hello")).isFalse();
    }

    @Test
    @DisplayName("a blocked command is never looked up when the list does not cover it")
    void skipsTheLookupForOrdinaryCommands() {
        ChatListener listener = listener();

        assertThat(listener.isBlockedCommand("/spawn")).isFalse();

        verify(this.client, never()).findActive(any(), any());
    }

    private ChatListener listener() {
        return new ChatListener(this.punishments, this.messages, this.config, Clock.fixed(NOW, ZoneOffset.UTC), LOGGER);
    }

    private AsyncChatEvent chatEvent() {
        return new AsyncChatEvent(
                true, this.player, new HashSet<>(),
                (source, sourceDisplayName, message, viewer) -> message, Component.text("hello"),
                Component.text("hello"), null
        );
    }

    private ActivePunishmentView permanentMute() {
        return new ActivePunishmentView(
                1L, PLAYER_UUID, "Steve",
                PunishmentKind.MUTE, BanType.PERMANENT,
                UUID.randomUUID(), "Admin",
                "spam", NOW.minus(Duration.ofDays(1)), null
        );
    }

}
