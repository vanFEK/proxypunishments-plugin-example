package net.vanfek.proxypunishments.core.reload;

import net.vanfek.proxypunishments.core.config.ConfigLoader;
import net.vanfek.proxypunishments.core.config.PluginConfig;
import net.vanfek.proxypunishments.core.config.RuntimeConfiguration;
import net.vanfek.proxypunishments.core.i18n.MessageBundle;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

/** Both files are read before either is swapped in, so a malformed one leaves the running plugin exactly as it was. */
public final class ConfigurationReloader {

    private static final int FAILURE_LIMIT = 200;

    private final Path dataDirectory;
    private final RuntimeConfiguration runtime;
    private final Logger logger;

    public ConfigurationReloader(
            @NotNull Path dataDirectory, @NotNull RuntimeConfiguration runtime,
            @NotNull Logger logger
    ) {
        this.dataDirectory = dataDirectory;
        this.runtime = runtime;
        this.logger = logger;
    }

    public @NotNull ReloadOutcome reload() {
        PluginConfig configured;
        MessageBundle messages;

        try {
            configured = new ConfigLoader(this.dataDirectory).load();
            messages = MessageBundle.load(this.dataDirectory);
        } catch (IOException | RuntimeException ex) {
            this.logger.error("Reload rejected — keeping the configuration the plugin started with", ex);

            return ReloadOutcome.rejected(describe(ex));
        }

        PluginConfig running = this.runtime.config();

        this.runtime.swap(configured, messages);
        this.logger.info("Reloaded config.yml and messages.yml from {}", this.dataDirectory);

        return ReloadOutcome.swapped(BoundSettings.changed(running, configured));
    }

    private static String describe(Exception ex) {
        String message = ex.getMessage();

        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }

        String firstLine = message.lines().findFirst().orElse("").strip();

        return firstLine.length() > FAILURE_LIMIT ? firstLine.substring(0, FAILURE_LIMIT) + "…" : firstLine;
    }

}
