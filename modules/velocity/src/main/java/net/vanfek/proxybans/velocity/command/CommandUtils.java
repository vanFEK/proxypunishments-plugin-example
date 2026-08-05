package net.vanfek.proxybans.velocity.command;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.vanfek.proxybans.core.client.BansHttpException;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static net.vanfek.proxybans.core.i18n.Placeholders.of;

public final class CommandUtils {

    private CommandUtils() {
    }

    public static void tell(CommandContext ctx, CommandSource source, String key, TagResolver... resolvers) {
        source.sendMessage(ctx.messages().render(key, resolvers));
    }

    /**
     * Turns a failed future into one log line with full detail and one chat line without it.
     *
     * <p>Response bodies from the service can carry stack traces and internal identifiers, so they go
     * to the console only — never into a chat message that other staff, or a screenshot, might see.
     */
    public static void reportFailure(CommandContext ctx, CommandSource source, Throwable throwable) {
        Throwable cause = unwrap(throwable);

        if (cause instanceof BansHttpException http && http.hasStatus()) {
            ctx.logger().warn("bans-service returned HTTP {}: {}", http.statusCode(), http.responseBody(), http);
            tell(ctx, source, "errors.service-status", of("status", http.statusCode()));

            return;
        }

        ctx.logger().warn("Command against bans-service failed: {}", cause.getMessage(), cause);
        tell(ctx, source, "errors.service-down");
    }

    public static CompletableFuture<Suggestions> suggestPlayers(ProxyServer proxy, SuggestionsBuilder builder) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);

        proxy.getAllPlayers().forEach(player -> {
            if (player.getUsername().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(player.getUsername());
            }
        });

        return builder.buildFuture();
    }

    public static Throwable unwrap(Throwable throwable) {
        return throwable instanceof CompletionException completion && completion.getCause() != null
                ? completion.getCause()
                : throwable;
    }

}
