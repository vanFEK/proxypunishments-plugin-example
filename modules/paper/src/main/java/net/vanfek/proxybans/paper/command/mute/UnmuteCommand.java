package net.vanfek.proxybans.paper.command.mute;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.vanfek.proxybans.api.PunishmentKind;
import net.vanfek.proxybans.api.platform.Actor;
import net.vanfek.proxybans.core.i18n.Placeholders;
import net.vanfek.proxybans.core.permission.PermissionNodes;
import net.vanfek.proxybans.paper.command.support.CommandContext;
import net.vanfek.proxybans.paper.command.support.Feedback;
import net.vanfek.proxybans.paper.platform.PaperActor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class UnmuteCommand {

    private UnmuteCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(CommandContext ctx, String literal) {
        return Commands.literal(literal)
                .requires(source -> ctx.permissions().allows(PaperActor.of(source.getSender()), PermissionNodes.UNMUTE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> execute(
                                ctx, PaperActor.of(context.getSource().getSender()),
                                StringArgumentType.getString(context, "player"), ""
                        ))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> execute(
                                        ctx, PaperActor.of(context.getSource().getSender()),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "reason")
                                ))))
                .build();
    }

    private static int execute(CommandContext ctx, Actor actor, String playerName, String reason) {
        ctx.punishments().resolveUuid(playerName)
                .thenCompose(resolved -> resolved
                        .map(uuid -> lift(ctx, actor, playerName, uuid, reason))
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

    private static CompletableFuture<Void> lift(
            CommandContext ctx, Actor actor,
            String playerName, UUID uuid, String reason
    ) {
        return ctx.punishments().lift(uuid, PunishmentKind.MUTE, actor.uuid(), actor.name(), reason)
                .thenRun(() -> Feedback.tell(
                        ctx, actor, "unmute.success",
                        Placeholders.of("player", playerName),
                        Placeholders.of("reason", reason.isBlank() ? "—" : reason)
                ));
    }

}
