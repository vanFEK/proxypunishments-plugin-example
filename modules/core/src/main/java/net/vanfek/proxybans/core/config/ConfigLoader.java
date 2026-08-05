package net.vanfek.proxybans.core.config;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

public final class ConfigLoader {

    private static final String CONFIG_FILE = "config.yml";

    private final Path dataDirectory;

    public ConfigLoader(@NotNull Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    public @NotNull PluginConfig load() throws IOException {
        Files.createDirectories(this.dataDirectory);

        Path target = this.dataDirectory.resolve(CONFIG_FILE);

        if (Files.notExists(target)) {
            copyDefault(target);
        }

        TypeSerializerCollection serializers = TypeSerializerCollection.defaults().childBuilder()
                .register(Duration.class, DurationTypeSerializer.INSTANCE)
                .build();

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(target)
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .defaultOptions(options -> options.serializers(serializers))
                .build();

        CommentedConfigurationNode root = loader.load();
        PluginConfig config = root.get(PluginConfig.class);

        if (config == null) {
            throw new ConfigurateException("Configuration could not be deserialized");
        }

        return config;
    }

    private void copyDefault(@NotNull Path target) throws IOException {
        try (InputStream in = ConfigLoader.class.getResourceAsStream("/" + CONFIG_FILE)) {
            if (in == null) {
                throw new IOException("Bundled default config.yml not found on classpath");
            }

            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

}
