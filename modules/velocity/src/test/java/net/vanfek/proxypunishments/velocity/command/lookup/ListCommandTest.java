package net.vanfek.proxypunishments.velocity.command.lookup;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.velocitypowered.api.command.CommandSource;
import net.vanfek.proxypunishments.api.PunishmentKind;
import net.vanfek.proxypunishments.core.PunishmentServiceImpl;
import net.vanfek.proxypunishments.core.cache.PunishmentCache;
import net.vanfek.proxypunishments.core.client.PunishmentsClient;
import net.vanfek.proxypunishments.core.config.PluginConfig;
import net.vanfek.proxypunishments.core.i18n.MessageBundle;
import net.vanfek.proxypunishments.core.permission.NativePermissionChecker;
import net.vanfek.proxypunishments.velocity.command.support.CommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListCommandTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Logger LOGGER = LoggerFactory.getLogger(ListCommandTest.class);

    private PunishmentsClient client;
    private CommandSource source;
    private CommandDispatcher<CommandSource> dispatcher;

    @BeforeEach
    void setUp(@TempDir Path directory) throws IOException {
        this.client = mock(PunishmentsClient.class);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        PunishmentCache cache = new PunishmentCache(new PluginConfig.Cache(), clock);
        PunishmentServiceImpl punishments = new PunishmentServiceImpl(this.client, cache);
        MessageBundle messages = MessageBundle.load(directory);

        CommandContext ctx = new CommandContext(punishments, messages, new NativePermissionChecker(), clock, LOGGER);

        this.source = mock(CommandSource.class);
        when(this.source.hasPermission(anyString())).thenReturn(true);

        this.dispatcher = new CommandDispatcher<>();
        this.dispatcher.getRoot().addChild(ListCommand.build(ctx, "banlist").getNode());

        when(this.client.listActive(any(), anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
    }

    @Test
    void listsBansByDefault() throws CommandSyntaxException {
        this.dispatcher.execute("banlist", this.source);

        verify(this.client).listActive(PunishmentKind.BAN, 10, 0);
    }

    @Test
    @DisplayName("a bare number is still a page, not a punishment kind")
    void keepsTheBarePageArgument() throws CommandSyntaxException {
        this.dispatcher.execute("banlist 2", this.source);

        verify(this.client).listActive(PunishmentKind.BAN, 10, 10);
    }

    @Test
    void listsMutesWhenAskedForThem() throws CommandSyntaxException {
        this.dispatcher.execute("banlist mute", this.source);

        verify(this.client).listActive(PunishmentKind.MUTE, 10, 0);
    }

    @Test
    void pagesWithinAKind() throws CommandSyntaxException {
        this.dispatcher.execute("banlist mute 3", this.source);

        verify(this.client).listActive(PunishmentKind.MUTE, 10, 20);
    }

    @Test
    void rejectsAnUnknownKind() throws CommandSyntaxException {
        this.dispatcher.execute("banlist nonsense", this.source);

        verify(this.client, never()).listActive(any(), anyInt(), anyInt());
    }

}
