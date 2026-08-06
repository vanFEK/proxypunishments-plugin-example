package net.vanfek.proxybans.velocity.command.support;

import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.vanfek.proxybans.core.client.BansHttpException;

import java.util.concurrent.CompletionException;

import static net.vanfek.proxybans.core.i18n.Placeholders.of;

public final class Feedback {

    private Feedback() {
    }

    public static void tell(CommandContext ctx, CommandSource source, String key, TagResolver... resolvers) {
        source.sendMessage(ctx.messages().render(key, resolvers));
    }

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

    private static Throwable unwrap(Throwable throwable) {
        return throwable instanceof CompletionException completion && completion.getCause() != null
                ? completion.getCause()
                : throwable;
    }

}
