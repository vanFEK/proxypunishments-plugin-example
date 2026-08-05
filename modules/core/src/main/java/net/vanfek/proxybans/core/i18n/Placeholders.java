package net.vanfek.proxybans.core.i18n;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

public final class Placeholders {

    private Placeholders() {
    }

    /**
     * Always {@link Placeholder#unparsed} — ban reasons and player names are attacker-controlled, and
     * parsing them as MiniMessage would let a player inject formatting, click actions or hover text
     * into staff-facing messages.
     */
    public static @NotNull TagResolver of(@NotNull String key, @NotNull Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

}
