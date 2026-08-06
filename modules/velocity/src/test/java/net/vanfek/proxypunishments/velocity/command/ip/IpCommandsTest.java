package net.vanfek.proxypunishments.velocity.command.ip;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.vanfek.proxypunishments.api.ActivePunishmentView;
import net.vanfek.proxypunishments.api.AltView;
import net.vanfek.proxypunishments.api.BanType;
import net.vanfek.proxypunishments.api.PunishmentKind;
import net.vanfek.proxypunishments.api.platform.Actor;
import net.vanfek.proxypunishments.core.PunishmentServiceImpl;
import net.vanfek.proxypunishments.core.cache.PunishmentCache;
import net.vanfek.proxypunishments.core.client.PunishmentsClient;
import net.vanfek.proxypunishments.core.config.PluginConfig;
import net.vanfek.proxypunishments.core.metrics.PluginMetrics;
import net.vanfek.proxypunishments.core.i18n.MessageBundle;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import net.vanfek.proxypunishments.core.permission.NativePermissionChecker;
import net.vanfek.proxypunishments.core.permission.PermissionNodes;
import net.vanfek.proxypunishments.velocity.command.support.CommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IpCommandsTest {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID ALT_UUID = UUID.fromString("22222222-3333-4444-5555-666666666666");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String ADDRESS = "203.0.113.7";
    private static final Logger LOGGER = LoggerFactory.getLogger(IpCommandsTest.class);

    private PunishmentsClient client;
    private MessageBundle messages;
    private ProxyServer proxy;
    private CommandSource source;
    private CommandDispatcher<CommandSource> dispatcher;

    @BeforeEach
    void setUp(@TempDir Path directory) throws IOException {
        this.client = mock(PunishmentsClient.class);
        this.messages = MessageBundle.load(directory);
        this.proxy = mock(ProxyServer.class);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        PunishmentCache cache = new PunishmentCache(new PluginConfig.Cache(), clock);
        PunishmentServiceImpl punishments = new PunishmentServiceImpl(this.client, cache, new PluginMetrics());

        CommandContext ctx = new CommandContext(punishments, this.messages, new NativePermissionChecker(), clock, LOGGER);

        this.source = mock(CommandSource.class);
        when(this.source.hasPermission(anyString())).thenReturn(true);

        this.dispatcher = new CommandDispatcher<>();
        this.dispatcher.getRoot().addChild(IpBanCommand.build(ctx, this.proxy, "ipban").getNode());
        this.dispatcher.getRoot().addChild(UnIpBanCommand.build(ctx, this.proxy, "unipban").getNode());
        this.dispatcher.getRoot().addChild(AltsCommand.build(ctx, this.proxy, "alts").getNode());

        when(this.proxy.getPlayer(anyString())).thenReturn(Optional.empty());
        when(this.client.lookup("Steve")).thenReturn(CompletableFuture.completedFuture(Optional.of(PLAYER_UUID)));
        when(this.client.issueIpBan(any(), any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(temporaryIpBan()));
        when(this.client.liftIpBan(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(this.client.alts(any(), anyInt())).thenReturn(CompletableFuture.completedFuture(List.of(alt())));
    }

    @Test
    @DisplayName("a bare address is banned as given, against the nil UUID")
    void bansALiteralAddress() throws CommandSyntaxException {
        this.dispatcher.execute("ipban " + ADDRESS + " 7d ban evasion", this.source);

        verify(this.client).issueIpBan(
                eq(ADDRESS), eq(Actor.CONSOLE_UUID),
                eq(Actor.CONSOLE_UUID), eq("Console"),
                eq("ban evasion"), eq(Duration.ofDays(7))
        );
    }

    @Test
    @DisplayName("a player name resolves to the address they are connected from, and to their UUID")
    void bansTheAddressOfAnOnlinePlayer() throws CommandSyntaxException {
        onlinePlayer();

        this.dispatcher.execute("ipban Steve ban evasion", this.source);

        verify(this.client).issueIpBan(
                eq(ADDRESS), eq(PLAYER_UUID),
                eq(Actor.CONSOLE_UUID), eq("Console"),
                eq("ban evasion"), isNull()
        );
    }

    @Test
    @DisplayName("a name that is neither an address nor an online player is reported, not guessed at")
    void refusesAnUnresolvableTarget() throws CommandSyntaxException {
        this.dispatcher.execute("ipban Steve ban evasion", this.source);

        verify(this.client, never()).issueIpBan(any(), any(), any(), any(), any(), any());
        assertOnlyMessage("errors.unknown-address", Placeholders.of("target", "Steve"));
    }

    @Test
    void rejectsAnIpBanWithoutAReason() throws CommandSyntaxException {
        this.dispatcher.execute("ipban " + ADDRESS + " 7d", this.source);

        verify(this.client, never()).issueIpBan(any(), any(), any(), any(), any(), any());
    }

    @Test
    void liftsAnIpBanForALiteralAddress() throws CommandSyntaxException {
        this.dispatcher.execute("unipban " + ADDRESS + " appeal accepted", this.source);

        verify(this.client).liftIpBan(eq(ADDRESS), eq(Actor.CONSOLE_UUID), eq("Console"), eq("appeal accepted"));
    }

    @Test
    @DisplayName("/alts resolves the name and asks the service for the other accounts")
    void listsAltsForAKnownPlayer() throws CommandSyntaxException {
        this.dispatcher.execute("alts Steve", this.source);

        verify(this.client).alts(eq(PLAYER_UUID), anyInt());
    }

    @Test
    @DisplayName("/alts on an unknown name reports it instead of asking for alts of nothing")
    void refusesAltsForAnUnknownPlayer() throws CommandSyntaxException {
        when(this.client.lookup("Ghost")).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        this.dispatcher.execute("alts Ghost", this.source);

        verify(this.client, never()).alts(any(), anyInt());
    }

    @Test
    @DisplayName("proxypunishments.admin alone unlocks all three commands")
    void adminCoversTheNewNodes() throws CommandSyntaxException {
        when(this.source.hasPermission(anyString())).thenReturn(false);
        when(this.source.hasPermission(PermissionNodes.ADMIN)).thenReturn(true);

        this.dispatcher.execute("ipban " + ADDRESS + " 7d ban evasion", this.source);
        this.dispatcher.execute("unipban " + ADDRESS, this.source);
        this.dispatcher.execute("alts Steve", this.source);

        verify(this.client).issueIpBan(any(), any(), any(), any(), any(), any());
        verify(this.client).liftIpBan(any(), any(), any(), any());
        verify(this.client).alts(eq(PLAYER_UUID), anyInt());
    }

    private void onlinePlayer() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(PLAYER_UUID);
        when(player.getRemoteAddress()).thenReturn(new InetSocketAddress(ADDRESS, 25565));

        when(this.proxy.getPlayer("Steve")).thenReturn(Optional.of(player));
    }

    private void assertOnlyMessage(String key, TagResolver... resolvers) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);

        verify(this.source).sendMessage(captor.capture());

        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

        assertThat(plain.serialize(captor.getValue())).isEqualTo(plain.serialize(this.messages.render(key, resolvers)));
    }

    private static AltView alt() {
        return new AltView(ALT_UUID, "Alex", NOW.minus(Duration.ofHours(1)), true);
    }

    private static ActivePunishmentView temporaryIpBan() {
        return new ActivePunishmentView(
                9L, PLAYER_UUID, "Steve",
                PunishmentKind.IP_BAN, BanType.TEMPORARY, ADDRESS,
                Actor.CONSOLE_UUID, "Console",
                "ban evasion", NOW, NOW.plus(Duration.ofDays(7))
        );
    }

}
