package net.vanfek.proxypunishments.paper.command.support;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.vanfek.proxypunishments.api.platform.Actor;
import net.vanfek.proxypunishments.core.client.PunishmentsHttpException;

import java.util.concurrent.CompletionException;

import static net.vanfek.proxypunishments.core.i18n.Placeholders.of;

public final class Feedback {

    private Feedback() {
    }

    public static void tell(CommandContext ctx, Actor actor, String key, TagResolver... resolvers) {
        actor.send(ctx.messages().render(key, resolvers));
    }

    public static void reportFailure(CommandContext ctx, Actor actor, Throwable throwable) {
        Throwable cause = unwrap(throwable);

        if (cause instanceof PunishmentsHttpException http && http.hasStatus()) {
            ctx.logger().warn("punishments-service returned HTTP {}: {}", http.statusCode(), http.responseBody(), http);
            tell(ctx, actor, "errors.service-status", of("status", http.statusCode()));

            return;
        }

        ctx.logger().warn("Command against punishments-service failed: {}", cause.getMessage(), cause);
        tell(ctx, actor, "errors.service-down");
    }

    private static Throwable unwrap(Throwable throwable) {
        return throwable instanceof CompletionException completion && completion.getCause() != null
                ? completion.getCause()
                : throwable;
    }

}
