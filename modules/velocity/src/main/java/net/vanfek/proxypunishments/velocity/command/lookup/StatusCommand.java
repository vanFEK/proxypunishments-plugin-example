package net.vanfek.proxypunishments.velocity.command.lookup;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.vanfek.proxypunishments.api.platform.Actor;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import net.vanfek.proxypunishments.core.metrics.PluginMetricsSnapshot;
import net.vanfek.proxypunishments.core.metrics.StatusFormat;
import net.vanfek.proxypunishments.core.permission.PermissionNodes;
import net.vanfek.proxypunishments.velocity.command.support.CommandContext;
import net.vanfek.proxypunishments.velocity.command.support.Feedback;
import net.vanfek.proxypunishments.velocity.platform.VelocityActor;

public final class StatusCommand {

    private StatusCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, String literal) {
        LiteralArgumentBuilder<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal(literal)
                .requires(source -> ctx.permissions().allows(VelocityActor.of(source), PermissionNodes.STATUS))
                .executes(context -> execute(ctx, VelocityActor.of(context.getSource())));

        return new BrigadierCommand(node);
    }

    private static int execute(CommandContext ctx, Actor actor) {
        PluginMetricsSnapshot snapshot = ctx.punishments().metrics().snapshot();

        Feedback.tell(ctx, actor, "status.header");

        Feedback.tell(
                ctx, actor, "status.cache",
                Placeholders.of("entries", ctx.punishments().cachedEntries()),
                Placeholders.of("hit_ratio", StatusFormat.percent(snapshot.hitRatio())),
                Placeholders.of("hits", snapshot.cacheHits()), Placeholders.of("misses", snapshot.cacheMisses()),
                Placeholders.of("stale", snapshot.staleAnswers())
        );

        Feedback.tell(
                ctx, actor, "status.service",
                Placeholders.of("calls", snapshot.serviceCalls()), Placeholders.of("failures", snapshot.serviceFailures()),
                Placeholders.of("average", StatusFormat.millis(snapshot.averageLatency())),
                Placeholders.of("slowest", StatusFormat.millis(snapshot.slowestLatency()))
        );

        if (!snapshot.invalidationsEnabled()) {
            Feedback.tell(ctx, actor, "status.redis-off");

            return Command.SINGLE_SUCCESS;
        }

        String key = snapshot.invalidationsSubscribed() ? "status.redis-connected" : "status.redis-disconnected";

        Feedback.tell(ctx, actor, key, Placeholders.of("events", snapshot.invalidationsReceived()));

        return Command.SINGLE_SUCCESS;
    }

}
