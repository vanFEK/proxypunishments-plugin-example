package net.vanfek.proxypunishments.core.config;

import net.vanfek.proxypunishments.core.i18n.MessageBundle;
import org.jetbrains.annotations.NotNull;

/** The configuration everything reads through, so a reload replaces both files at once or neither. */
public final class RuntimeConfiguration {

    private volatile Snapshot snapshot;

    public RuntimeConfiguration(@NotNull PluginConfig config, @NotNull MessageBundle messages) {
        this.snapshot = new Snapshot(config, messages);
    }

    public @NotNull PluginConfig config() {
        return this.snapshot.config();
    }

    public @NotNull MessageBundle messages() {
        return this.snapshot.messages();
    }

    public void swap(@NotNull PluginConfig config, @NotNull MessageBundle messages) {
        this.snapshot = new Snapshot(config, messages);
    }

    private record Snapshot(PluginConfig config, MessageBundle messages) {
    }

}
