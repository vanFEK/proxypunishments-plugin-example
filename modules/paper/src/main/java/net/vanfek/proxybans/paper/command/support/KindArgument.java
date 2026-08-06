package net.vanfek.proxybans.paper.command.support;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.vanfek.proxybans.api.PunishmentKind;
import net.vanfek.proxybans.core.command.PunishmentKinds;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class KindArgument {

    private KindArgument() {
    }

    public static @NotNull Optional<PunishmentKind> parse(@NotNull String token) {
        return PunishmentKinds.parse(token);
    }

    public static @NotNull CompletableFuture<Suggestions> matching(@NotNull SuggestionsBuilder builder) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (String kind : PunishmentKinds.names()) {
            if (kind.startsWith(prefix)) {
                builder.suggest(kind);
            }
        }

        return builder.buildFuture();
    }

}
