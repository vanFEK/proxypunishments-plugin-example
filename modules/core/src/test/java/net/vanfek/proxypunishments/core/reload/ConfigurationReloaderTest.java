package net.vanfek.proxypunishments.core.reload;

import net.vanfek.proxypunishments.core.config.PluginConfig;
import net.vanfek.proxypunishments.core.config.RuntimeConfiguration;
import net.vanfek.proxypunishments.core.i18n.MessageBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationReloaderTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationReloaderTest.class);

    private Path directory;
    private PluginConfig config;
    private MessageBundle messages;
    private RuntimeConfiguration runtime;
    private ConfigurationReloader reloader;

    @BeforeEach
    void setUp(@TempDir Path directory) throws IOException {
        this.directory = directory;
        this.config = new PluginConfig();
        this.messages = MessageBundle.load(directory);
        this.runtime = new RuntimeConfiguration(this.config, this.messages);
        this.reloader = new ConfigurationReloader(directory, this.runtime, LOGGER);
    }

    @Test
    @DisplayName("what the files say after the reload is what the plugin reads")
    void swapsBothFiles() throws IOException {
        write("config.yml", "login:\n  fail-open: false\n  remember-player: false\n");
        write("messages.yml", "reload:\n  success: \"перечитано\"\n");

        ReloadOutcome outcome = this.reloader.reload();

        assertThat(outcome.applied()).isTrue();
        assertThat(this.runtime.config().login.failOpen).isFalse();
        assertThat(this.runtime.config().login.rememberPlayer).isFalse();
        assertThat(this.runtime.messages().raw("reload.success")).isEqualTo("перечитано");
    }

    @Test
    @DisplayName("keys the operator did not write still resolve from the bundled defaults")
    void keepsTheBundledDefaultsBehindTheReloadedFile() throws IOException {
        write("messages.yml", "reload:\n  success: \"перечитано\"\n");

        this.reloader.reload();

        assertThat(this.runtime.messages().raw("status.header")).isEqualTo(this.messages.raw("status.header"));
    }

    @Test
    @DisplayName("a malformed config.yml is rejected and nothing the plugin runs on moves")
    void keepsTheRunningConfigurationWhenTheConfigIsMalformed() throws IOException {
        write("config.yml", "service:\n  base-url: \"unterminated\n");
        write("messages.yml", "reload:\n  success: \"перечитано\"\n");

        ReloadOutcome outcome = this.reloader.reload();

        assertThat(outcome.applied()).isFalse();
        assertThat(outcome.failure()).isNotBlank();
        assertThat(this.runtime.config()).isSameAs(this.config);
        assertThat(this.runtime.messages()).isSameAs(this.messages);
    }

    @Test
    @DisplayName("a malformed messages.yml holds back the configuration that parsed fine")
    void keepsTheRunningConfigurationWhenTheMessagesAreMalformed() throws IOException {
        write("config.yml", "login:\n  fail-open: false\n");
        write("messages.yml", "reload:\n  success: \"unterminated\n");

        ReloadOutcome outcome = this.reloader.reload();

        assertThat(outcome.applied()).isFalse();
        assertThat(this.runtime.config()).isSameAs(this.config);
        assertThat(this.runtime.config().login.failOpen).isTrue();
    }

    @Test
    @DisplayName("settings bound at startup are named with both values, and secrets are not echoed")
    void namesTheSettingsThatNeedARestart() throws IOException {
        write("config.yml", "service:\n  base-url: \"http://elsewhere:9000\"\n  token: \"s3cret\"\n"
                + "cache:\n  max-size: 5\nredis:\n  enabled: true\n");

        ReloadOutcome outcome = this.reloader.reload();

        assertThat(outcome.needingRestart()).contains(
                new BoundSetting("service.base-url", "http://proxypunishments:8080", "http://elsewhere:9000"),
                new BoundSetting("cache.max-size", "100000", "5"),
                new BoundSetting("redis.enabled", "false", "true"),
                new BoundSetting("service.token", "***", "***")
        );
    }

    @Test
    @DisplayName("settings the listeners read live are not reported as needing a restart")
    void leavesTheLiveSettingsOutOfTheRestartList() throws IOException {
        write("config.yml", "login:\n  fail-open: false\nchat:\n  enforce-mute: false\n");

        ReloadOutcome outcome = this.reloader.reload();

        assertThat(outcome.needingRestart()).isEmpty();
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(this.directory.resolve(name), content, StandardCharsets.UTF_8);
    }

}
