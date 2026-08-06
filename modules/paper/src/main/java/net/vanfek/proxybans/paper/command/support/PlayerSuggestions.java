package net.vanfek.proxybans.paper.command.support;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class PlayerSuggestions {

    private PlayerSuggestions() {
    }

    public static CompletableFuture<Suggestions> matching(Server server, SuggestionsBuilder builder) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (Player player : server.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(player.getName());
            }
        }

        return builder.buildFuture();
    }

}
