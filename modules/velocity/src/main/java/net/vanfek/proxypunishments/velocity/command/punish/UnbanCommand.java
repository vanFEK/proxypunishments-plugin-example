package net.vanfek.proxypunishments.velocity.command.punish;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.vanfek.proxypunishments.api.PunishmentKind;
import net.vanfek.proxypunishments.api.platform.Actor;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import net.vanfek.proxypunishments.core.permission.PermissionNodes;
import net.vanfek.proxypunishments.velocity.command.support.CommandContext;
import net.vanfek.proxypunishments.velocity.command.support.Feedback;
import net.vanfek.proxypunishments.velocity.platform.VelocityActor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class UnbanCommand {

    private UnbanCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, String literal) {
        LiteralArgumentBuilder<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal(literal)
                .requires(source -> ctx.permissions().allows(VelocityActor.of(source), PermissionNodes.UNBAN))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                        .executes(context -> execute(
                                ctx, VelocityActor.of(context.getSource()),
                                StringArgumentType.getString(context, "player"), ""
                        ))
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("reason", StringArgumentType.greedyString())
                                .executes(context -> execute(
                                        ctx, VelocityActor.of(context.getSource()),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "reason")
                                ))));

        return new BrigadierCommand(node);
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
        return ctx.punishments().lift(uuid, PunishmentKind.BAN, actor.uuid(), actor.name(), reason)
                .thenRun(() -> Feedback.tell(
                        ctx, actor, "unban.success",
                        Placeholders.of("player", playerName),
                        Placeholders.of("reason", reason.isBlank() ? "—" : reason)
                ));
    }

}
