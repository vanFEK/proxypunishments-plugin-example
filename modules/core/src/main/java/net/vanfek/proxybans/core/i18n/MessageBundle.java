package net.vanfek.proxybans.core.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** The bundled defaults sit behind the operator's file so keys added in later versions still resolve. */
public final class MessageBundle {

    private static final String MESSAGES_FILE = "messages.yml";

    private final MiniMessage miniMessage;
    private final Map<String, String> templates;
    private final Map<String, String> defaults;

    private MessageBundle(@NotNull Map<String, String> templates, @NotNull Map<String, String> defaults) {
        this.miniMessage = MiniMessage.miniMessage();
        this.templates = templates;
        this.defaults = defaults;
    }

    public static @NotNull MessageBundle load(@NotNull Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);

        Path target = dataDirectory.resolve(MESSAGES_FILE);

        if (Files.notExists(target)) {
            copyDefault(target);
        }

        Map<String, String> defaults = loadDefaults();
        Map<String, String> templates = new LinkedHashMap<>();

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(target)
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .build();

        flatten(loader.load(), "", templates);

        return new MessageBundle(templates, defaults);
    }

    public static @NotNull MessageBundle ofDefaults() throws IOException {
        Map<String, String> defaults = loadDefaults();

        return new MessageBundle(new LinkedHashMap<>(defaults), defaults);
    }

    public @NotNull Component render(@NotNull String key, @NotNull TagResolver... resolvers) {
        return this.miniMessage.deserialize(raw(key), resolvers);
    }

    public @NotNull String raw(@NotNull String key) {
        String template = this.templates.get(key);

        if (template != null) {
            return template;
        }

        return this.defaults.getOrDefault(key, "<red>missing message: " + key + "</red>");
    }

    public boolean hasKey(@NotNull String key) {
        return this.templates.containsKey(key) || this.defaults.containsKey(key);
    }

    private static Map<String, String> loadDefaults() throws IOException {
        String bundled;

        try (InputStream in = MessageBundle.class.getResourceAsStream("/" + MESSAGES_FILE)) {
            if (in == null) {
                throw new IOException("Bundled default messages.yml not found on classpath");
            }

            bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(bundled)))
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .build();

        Map<String, String> defaults = new LinkedHashMap<>();

        flatten(loader.load(), "", defaults);

        return defaults;
    }

    private static void flatten(CommentedConfigurationNode node, String prefix, Map<String, String> out) throws ConfigurateException {
        if (node.isMap()) {
            for (Map.Entry<Object, CommentedConfigurationNode> entry : node.childrenMap().entrySet()) {
                String key = prefix.isEmpty() ? entry.getKey().toString() : prefix + "." + entry.getKey();

                flatten(entry.getValue(), key, out);
            }

            return;
        }

        if (node.virtual()) {
            return;
        }

        String value = node.getString();

        if (value != null) {
            out.put(prefix, value);
        }
    }

    private static void copyDefault(Path target) throws IOException {
        try (InputStream in = MessageBundle.class.getResourceAsStream("/" + MESSAGES_FILE)) {
            if (in == null) {
                throw new IOException("Bundled default messages.yml not found on classpath");
            }

            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

}
