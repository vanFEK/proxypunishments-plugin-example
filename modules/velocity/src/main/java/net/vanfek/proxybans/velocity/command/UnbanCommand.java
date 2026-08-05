package net.vanfek.proxybans.velocity.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.vanfek.proxybans.core.i18n.Placeholders;
import net.vanfek.proxybans.core.permission.PermissionNodes;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class UnbanCommand {

    private UnbanCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, String literal) {
        LiteralArgumentBuilder<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal(literal)
                .requires(source -> ctx.permissions().allows(source, PermissionNodes.UNBAN))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                        .executes(context -> execute(
                                ctx,
                                context.getSource(),
                                StringArgumentType.getString(context, "player"),
                                ""
                        ))
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("reason", StringArgumentType.greedyString())
                                .executes(context -> execute(
                                        ctx,
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "reason")
                                ))));

        return new BrigadierCommand(node);
    }

    private static int execute(CommandContext ctx, CommandSource source, String playerName, String reason) {
        Issuer issuer = Issuer.of(source);

        ctx.bans().resolveUuid(playerName)
                .thenCompose(resolved -> resolved
                        .map(uuid -> lift(ctx, source, playerName, uuid, reason, issuer))
                        .orElseGet(() -> {
                            CommandUtils.tell(ctx, source, "errors.unknown-player", Placeholders.of("player", playerName));

                            return CompletableFuture.completedFuture(null);
                        }))
                .exceptionally(throwable -> {
                    CommandUtils.reportFailure(ctx, source, throwable);

                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Void> lift(
            CommandContext ctx,
            CommandSource source,
            String playerName,
            UUID uuid,
            String reason,
            Issuer issuer
    ) {
        return ctx.bans().liftBan(uuid, issuer.uuid(), issuer.name(), reason)
                .thenRun(() -> CommandUtils.tell(
                        ctx,
                        source,
                        "unban.success",
                        Placeholders.of("player", playerName),
                        Placeholders.of("reason", reason.isBlank() ? "—" : reason)
                ));
    }

}
