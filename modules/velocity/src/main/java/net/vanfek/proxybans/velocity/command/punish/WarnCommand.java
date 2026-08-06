package net.vanfek.proxybans.velocity.command.punish;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.vanfek.proxybans.api.platform.Actor;
import net.vanfek.proxybans.core.i18n.Placeholders;
import net.vanfek.proxybans.core.permission.PermissionNodes;
import net.vanfek.proxybans.velocity.command.support.CommandContext;
import net.vanfek.proxybans.velocity.command.support.Feedback;
import net.vanfek.proxybans.velocity.command.support.PlayerSuggestions;
import net.vanfek.proxybans.velocity.platform.VelocityActor;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class WarnCommand {

    private WarnCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, ProxyServer proxy, String literal) {
        LiteralArgumentBuilder<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal(literal)
                .requires(source -> ctx.permissions().allows(VelocityActor.of(source), PermissionNodes.WARN))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> PlayerSuggestions.matching(proxy, builder))
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("reason", StringArgumentType.greedyString())
                                .executes(context -> execute(
                                        ctx, proxy, VelocityActor.of(context.getSource()),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "reason")
                                ))));

        return new BrigadierCommand(node);
    }

    private static int execute(CommandContext ctx, ProxyServer proxy, Actor actor, String playerName, String reason) {
        String trimmed = reason.trim();

        if (trimmed.isEmpty()) {
            Feedback.tell(ctx, actor, "errors.warn-reason-required");

            return Command.SINGLE_SUCCESS;
        }

        ctx.punishments().resolveUuid(playerName)
                .thenCompose(resolved -> resolved
                        .map(uuid -> issue(ctx, proxy, actor, playerName, uuid, trimmed))
                        .orElseGet(() -> {
                            Feedback.tell(ctx, actor, "errors.unknown-player", Placeholders.of("player", playerName));

                            return CompletableFuture.completedFuture(null);
                        }))
                .exceptionally(throwable -> {
                    Feedback.reportFailure(ctx, actor, throwable);

                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Void> issue(
            CommandContext ctx, ProxyServer proxy, Actor actor,
            String playerName, UUID uuid, String reason
    ) {
        return ctx.punishments().warn(uuid, actor.uuid(), actor.name(), reason)
                .thenAccept(entry -> {
                    notifyTarget(ctx, proxy, actor, uuid, reason);

                    Feedback.tell(
                            ctx, actor, "warn.success",
                            Placeholders.of("player", playerName), Placeholders.of("reason", reason)
                    );
                });
    }

    private static void notifyTarget(CommandContext ctx, ProxyServer proxy, Actor actor, UUID uuid, String reason) {
        Optional<Player> online = proxy.getPlayer(uuid);

        if (online.isEmpty()) {
            return;
        }

        online.get().sendMessage(ctx.messages().render(
                "warn.player-reason",
                Placeholders.of("reason", reason), Placeholders.of("admin", actor.name())
        ));
    }

}
