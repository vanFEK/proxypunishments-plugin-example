package net.vanfek.proxypunishments.velocity.command.admin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.vanfek.proxypunishments.core.PunishmentServiceImpl;
import net.vanfek.proxypunishments.core.cache.PunishmentCache;
import net.vanfek.proxypunishments.core.client.PunishmentsClient;
import net.vanfek.proxypunishments.core.config.PluginConfig;
import net.vanfek.proxypunishments.core.config.RuntimeConfiguration;
import net.vanfek.proxypunishments.core.i18n.MessageBundle;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import net.vanfek.proxypunishments.core.metrics.PluginMetrics;
import net.vanfek.proxypunishments.core.permission.NativePermissionChecker;
import net.vanfek.proxypunishments.core.permission.PermissionNodes;
import net.vanfek.proxypunishments.core.reload.ConfigurationReloader;
import net.vanfek.proxypunishments.velocity.command.support.CommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReloadCommandTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Logger LOGGER = LoggerFactory.getLogger(ReloadCommandTest.class);
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private Path directory;
    private PluginConfig config;
    private MessageBundle messages;
    private RuntimeConfiguration runtime;
    private ConfigurationReloader reloader;
    private CommandSource source;
    private CommandDispatcher<CommandSource> dispatcher;

    @BeforeEach
    void setUp(@TempDir Path directory) throws IOException {
        this.directory = directory;
        this.config = new PluginConfig();
        this.messages = MessageBundle.load(directory);
        this.runtime = new RuntimeConfiguration(this.config, this.messages);
        this.reloader = new ConfigurationReloader(directory, this.runtime, LOGGER);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        PunishmentCache cache = new PunishmentCache(this.config.cache, clock);
        PunishmentsClient client = mock(PunishmentsClient.class);
        PunishmentServiceImpl punishments = new PunishmentServiceImpl(client, cache, new PluginMetrics());

        CommandContext ctx = new CommandContext(
                punishments, this.runtime,
                this.reloader, new NativePermissionChecker(),
                clock, LOGGER
        );

        this.source = mock(CommandSource.class);
        when(this.source.hasPermission(anyString())).thenReturn(true);

        this.dispatcher = new CommandDispatcher<>();
        this.dispatcher.getRoot().addChild(ReloadCommand.build(ctx, "reload").getNode());
    }

    @Test
    @DisplayName("the reply is rendered from the messages.yml that was just read")
    void appliesBothFilesAndAnswersFromTheNewOne() throws CommandSyntaxException, IOException {
        write("config.yml", "login:\n  fail-open: false\n");
        write("messages.yml", "reload:\n  success: \"перечитано\"\n");

        this.dispatcher.execute("reload", this.source);

        assertThat(this.runtime.config().login.failOpen).isFalse();
        assertThat(replies()).containsExactly("перечитано");
    }

    @Test
    @DisplayName("a malformed config.yml is reported and the running configuration is left alone")
    void keepsTheRunningConfigurationWhenTheFileIsMalformed() throws CommandSyntaxException, IOException {
        write("config.yml", "service:\n  base-url: \"unterminated\n");

        this.dispatcher.execute("reload", this.source);

        assertThat(this.runtime.config()).isSameAs(this.config);
        assertThat(this.runtime.messages()).isSameAs(this.messages);
        assertThat(replies()).containsExactly(rendered("reload.failed", Placeholders.of("error", rejection())));
    }

    @Test
    @DisplayName("the reply names every setting the reload could not move, and says a restart is needed")
    void namesTheSettingsThatNeedARestart() throws CommandSyntaxException, IOException {
        write("config.yml", "service:\n  base-url: \"http://elsewhere:9000\"\ncache:\n  max-size: 5\n");

        this.dispatcher.execute("reload", this.source);

        assertThat(replies()).containsExactly(
                rendered("reload.success"),
                rendered("reload.restart-header"),
                rendered(
                        "reload.restart-entry", Placeholders.of("setting", "service.base-url"),
                        Placeholders.of("running", "http://proxypunishments:8080"),
                        Placeholders.of("configured", "http://elsewhere:9000")
                ),
                rendered(
                        "reload.restart-entry", Placeholders.of("setting", "cache.max-size"),
                        Placeholders.of("running", "100000"), Placeholders.of("configured", "5")
                )
        );
    }

    @Test
    @DisplayName("proxypunishments.admin alone unlocks the command")
    void adminCoversTheReloadNode() throws CommandSyntaxException {
        when(this.source.hasPermission(anyString())).thenReturn(false);
        when(this.source.hasPermission(PermissionNodes.ADMIN)).thenReturn(true);

        this.dispatcher.execute("reload", this.source);

        assertThat(replies()).containsExactly(rendered("reload.success"));
    }

    private String rejection() {
        return this.reloader.reload().failure();
    }

    private List<String> replies() {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);

        verify(this.source, atLeastOnce()).sendMessage(captor.capture());

        return captor.getAllValues().stream().map(PLAIN::serialize).toList();
    }

    private String rendered(String key, TagResolver... resolvers) {
        return PLAIN.serialize(this.messages.render(key, resolvers));
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(this.directory.resolve(name), content, StandardCharsets.UTF_8);
    }

}
