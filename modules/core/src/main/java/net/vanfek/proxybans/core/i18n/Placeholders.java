package net.vanfek.proxybans.core.i18n;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

public final class Placeholders {

    private Placeholders() {
    }

    // Unparsed on purpose: a ban reason must never be able to smuggle MiniMessage markup into a rendered message.
    public static @NotNull TagResolver of(@NotNull String key, @NotNull Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

}
