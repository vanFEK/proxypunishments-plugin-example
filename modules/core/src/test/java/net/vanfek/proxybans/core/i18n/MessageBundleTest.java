package net.vanfek.proxybans.core.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MessageBundleTest {

    @Test
    void writesDefaultsOnFirstLoad(@TempDir Path directory) throws IOException {
        MessageBundle.load(directory);

        assertThat(directory.resolve("messages.yml")).exists();
    }

    @Test
    @DisplayName("an operator file from an older version still resolves keys added later")
    void fallsBackToBundledDefaultsForUnknownKeys(@TempDir Path directory) throws IOException {
        Files.writeString(
                directory.resolve("messages.yml"),
                "errors:\n  service-down: \"custom outage text\"\n",
                StandardCharsets.UTF_8
        );

        MessageBundle bundle = MessageBundle.load(directory);

        assertThat(bundle.raw("errors.service-down")).isEqualTo("custom outage text");
        assertThat(bundle.raw("errors.unknown-player")).isNotBlank();
        assertThat(bundle.raw("errors.unknown-player")).doesNotContain("missing message");
    }

    @Test
    @DisplayName("a placeholder value is inserted verbatim and never parsed as MiniMessage")
    void doesNotParsePlaceholderValuesAsMarkup(@TempDir Path directory) throws IOException {
        Files.writeString(
                directory.resolve("messages.yml"),
                "test:\n  line: \"reason: <reason>\"\n",
                StandardCharsets.UTF_8
        );

        MessageBundle bundle = MessageBundle.load(directory);
        Component rendered = bundle.render("test.line", Placeholders.of("reason", "<red><bold>gotcha</bold></red>"));

        String plain = PlainTextComponentSerializer.plainText().serialize(rendered);

        assertThat(plain).isEqualTo("reason: <red><bold>gotcha</bold></red>");
    }

    @Test
    void reportsTrulyMissingKeys(@TempDir Path directory) throws IOException {
        MessageBundle bundle = MessageBundle.load(directory);

        assertThat(bundle.hasKey("errors.service-down")).isTrue();
        assertThat(bundle.hasKey("nothing.here")).isFalse();
        assertThat(bundle.raw("nothing.here")).contains("missing message");
    }

}
