package net.vanfek.proxybans.velocity.command.support;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class PlayerSuggestions {

    private PlayerSuggestions() {
    }

    public static CompletableFuture<Suggestions> matching(ProxyServer proxy, SuggestionsBuilder builder) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);

        proxy.getAllPlayers().forEach(player -> {
            if (player.getUsername().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(player.getUsername());
            }
        });

        return builder.buildFuture();
    }

}
