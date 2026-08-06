package net.vanfek.proxypunishments.paper.command.punish;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.vanfek.proxypunishments.api.platform.Actor;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import net.vanfek.proxypunishments.core.permission.PermissionNodes;
import net.vanfek.proxypunishments.paper.command.support.CommandContext;
import net.vanfek.proxypunishments.paper.command.support.Feedback;
import net.vanfek.proxypunishments.paper.command.support.PlayerSuggestions;
import net.vanfek.proxypunishments.paper.platform.PaperActor;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class WarnCommand {

    private WarnCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(CommandContext ctx, Server server, String literal) {
        return Commands.literal(literal)
                .requires(source -> ctx.permissions().allows(PaperActor.of(source.getSender()), PermissionNodes.WARN))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> PlayerSuggestions.matching(server, builder))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> execute(
                                        ctx, server, PaperActor.of(context.getSource().getSender()),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "reason")
                                ))))
                .build();
    }

    private static int execute(CommandContext ctx, Server server, Actor actor, String playerName, String reason) {
        String trimmed = reason.trim();

        if (trimmed.isEmpty()) {
            Feedback.tell(ctx, actor, "errors.warn-reason-required");

            return Command.SINGLE_SUCCESS;
        }

        ctx.punishments().resolveUuid(playerName)
                .thenCompose(resolved -> resolved
                        .map(uuid -> issue(ctx, server, actor, playerName, uuid, trimmed))
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
            CommandContext ctx, Server server, Actor actor,
            String playerName, UUID uuid, String reason
    ) {
        return ctx.punishments().warn(uuid, actor.uuid(), actor.name(), reason)
                .thenAccept(entry -> {
                    notifyTarget(ctx, server, actor, uuid, reason);

                    Feedback.tell(
                            ctx, actor, "warn.success",
                            Placeholders.of("player", playerName), Placeholders.of("reason", reason)
                    );
                });
    }

    private static void notifyTarget(CommandContext ctx, Server server, Actor actor, UUID uuid, String reason) {
        Player online = server.getPlayer(uuid);

        if (online == null) {
            return;
        }

        online.sendMessage(ctx.messages().render(
                "warn.player-reason",
                Placeholders.of("reason", reason), Placeholders.of("admin", actor.name())
        ));
    }

}
