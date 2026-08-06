package net.vanfek.proxybans.velocity.command.mute;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.vanfek.proxybans.api.ActivePunishmentView;
import net.vanfek.proxybans.api.BanType;
import net.vanfek.proxybans.api.PunishmentKind;
import net.vanfek.proxybans.api.platform.Actor;
import net.vanfek.proxybans.core.PunishmentServiceImpl;
import net.vanfek.proxybans.core.cache.PunishmentCache;
import net.vanfek.proxybans.core.client.PunishmentsClient;
import net.vanfek.proxybans.core.config.PluginConfig;
import net.vanfek.proxybans.core.i18n.MessageBundle;
import net.vanfek.proxybans.core.permission.NativePermissionChecker;
import net.vanfek.proxybans.velocity.command.support.CommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MuteCommandTest {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Logger LOGGER = LoggerFactory.getLogger(MuteCommandTest.class);

    private PunishmentsClient client;
    private MessageBundle messages;
    private CommandSource source;
    private CommandDispatcher<CommandSource> dispatcher;

    @BeforeEach
    void setUp(@TempDir Path directory) throws IOException {
        this.client = mock(PunishmentsClient.class);
        this.messages = MessageBundle.load(directory);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        PunishmentCache cache = new PunishmentCache(new PluginConfig.Cache(), clock);
        PunishmentServiceImpl punishments = new PunishmentServiceImpl(this.client, cache);

        CommandContext ctx = new CommandContext(punishments, this.messages, new NativePermissionChecker(), clock, LOGGER);
        ProxyServer proxy = mock(ProxyServer.class);

        this.source = mock(CommandSource.class);
        when(this.source.hasPermission(anyString())).thenReturn(true);

        this.dispatcher = new CommandDispatcher<>();
        this.dispatcher.getRoot().addChild(MuteCommand.build(ctx, proxy, "mute").getNode());
        this.dispatcher.getRoot().addChild(TempMuteCommand.build(ctx, proxy, "tempmute").getNode());

        when(this.client.lookup("Steve")).thenReturn(CompletableFuture.completedFuture(Optional.of(PLAYER_UUID)));
        when(this.client.issue(any(), any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(temporaryMute()));
    }

    @Test
    @DisplayName("a leading duration becomes the mute length and the rest the reason")
    void splitsDurationFromReason() throws CommandSyntaxException {
        this.dispatcher.execute("mute Steve 7d spamming the chat", this.source);

        verify(this.client).issue(
                eq(PLAYER_UUID), eq(PunishmentKind.MUTE),
                eq(Actor.CONSOLE_UUID), eq("Console"),
                eq("spamming the chat"), eq(Duration.ofDays(7))
        );
    }

    @Test
    @DisplayName("a mute without a leading duration is permanent and keeps the whole reason")
    void treatsAReasonOnlyTailAsPermanent() throws CommandSyntaxException {
        this.dispatcher.execute("mute Steve flooding with links", this.source);

        verify(this.client).issue(
                eq(PLAYER_UUID), eq(PunishmentKind.MUTE),
                eq(Actor.CONSOLE_UUID), eq("Console"),
                eq("flooding with links"), isNull()
        );
    }

    @Test
    void rejectsAMuteWithoutAReason() throws CommandSyntaxException {
        this.dispatcher.execute("mute Steve 7d", this.source);

        assertOnlyMessage("errors.reason-required");
        verify(this.client, never()).issue(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("an out-of-range duration is reported instead of being sent to the service")
    void rejectsAnUnparseableDuration() throws CommandSyntaxException {
        this.dispatcher.execute("mute Steve 400y spam", this.source);

        verify(this.client, never()).issue(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("/tempmute refuses a permanent token and points at /mute")
    void tempMuteRefusesPermanentDurations() throws CommandSyntaxException {
        this.dispatcher.execute("tempmute Steve perm spam", this.source);

        assertOnlyMessage("errors.tempmute-requires-duration");
        verify(this.client, never()).issue(any(), any(), any(), any(), any(), any());
    }

    @Test
    void tempMuteIssuesAMuteForTheGivenDuration() throws CommandSyntaxException {
        this.dispatcher.execute("tempmute Steve 2h30m spam", this.source);

        verify(this.client).issue(
                eq(PLAYER_UUID), eq(PunishmentKind.MUTE),
                eq(Actor.CONSOLE_UUID), eq("Console"),
                eq("spam"), eq(Duration.ofMinutes(150))
        );
    }

    private void assertOnlyMessage(String key) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);

        verify(this.source).sendMessage(captor.capture());

        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

        assertThat(plain.serialize(captor.getValue())).isEqualTo(plain.serialize(this.messages.render(key)));
    }

    private static ActivePunishmentView temporaryMute() {
        return new ActivePunishmentView(
                1L, PLAYER_UUID, "Steve",
                PunishmentKind.MUTE, BanType.TEMPORARY,
                UUID.randomUUID(), "Console",
                "spam", NOW, NOW.plus(Duration.ofDays(7))
        );
    }

}
