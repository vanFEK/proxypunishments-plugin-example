package net.vanfek.proxypunishments.velocity.command.punish;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.vanfek.proxypunishments.api.PunishmentHistoryEntry;
import net.vanfek.proxypunishments.api.platform.Actor;
import net.vanfek.proxypunishments.core.PunishmentServiceImpl;
import net.vanfek.proxypunishments.core.cache.PunishmentCache;
import net.vanfek.proxypunishments.core.client.PunishmentsClient;
import net.vanfek.proxypunishments.core.config.PluginConfig;
import net.vanfek.proxypunishments.core.i18n.MessageBundle;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import net.vanfek.proxypunishments.core.permission.NativePermissionChecker;
import net.vanfek.proxypunishments.velocity.command.support.CommandContext;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WarnCommandTest {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Logger LOGGER = LoggerFactory.getLogger(WarnCommandTest.class);

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
        this.dispatcher.getRoot().addChild(WarnCommand.build(ctx, proxy, "warn").getNode());

        when(this.client.lookup("Steve")).thenReturn(CompletableFuture.completedFuture(Optional.of(PLAYER_UUID)));
        when(this.client.warn(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(entry()));
    }

    @Test
    void logsTheWarningWithTheWholeReason() throws CommandSyntaxException {
        this.dispatcher.execute("warn Steve spamming in chat", this.source);

        verify(this.client).warn(PLAYER_UUID, Actor.CONSOLE_UUID, "Console", "spamming in chat");
        assertOnlyMessage(
                "warn.success",
                Placeholders.of("player", "Steve"), Placeholders.of("reason", "spamming in chat")
        );
    }

    @Test
    @DisplayName("a reason made of nothing but spaces is refused before the service is touched")
    void refusesABlankReason() throws CommandSyntaxException {
        this.dispatcher.execute("warn Steve   ", this.source);

        assertOnlyMessage("errors.warn-reason-required");
        verify(this.client, never()).warn(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a warning never asks the service for a duration or a lift")
    void neverIssuesOrLifts() throws CommandSyntaxException {
        this.dispatcher.execute("warn Steve ban evasion", this.source);

        verify(this.client, never()).issue(any(), any(), any(), any(), any(), any());
        verify(this.client, never()).lift(any(), any(), any(), any(), any());
    }

    private void assertOnlyMessage(String key, TagResolver... resolvers) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);

        verify(this.source).sendMessage(captor.capture());

        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

        assertThat(plain.serialize(captor.getValue())).isEqualTo(plain.serialize(this.messages.render(key, resolvers)));
    }

    private static PunishmentHistoryEntry entry() {
        return new PunishmentHistoryEntry(
                PLAYER_UUID, Actor.CONSOLE_UUID, "Console",
                PunishmentHistoryEntry.Action.WARN, "spamming in chat",
                NOW, null
        );
    }

}
