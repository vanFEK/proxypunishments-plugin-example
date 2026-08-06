package net.vanfek.proxypunishments.velocity.command.ip;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.vanfek.proxypunishments.api.AltView;
import net.vanfek.proxypunishments.api.platform.Actor;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import net.vanfek.proxypunishments.core.permission.PermissionNodes;
import net.vanfek.proxypunishments.velocity.command.support.CommandContext;
import net.vanfek.proxypunishments.velocity.command.support.Feedback;
import net.vanfek.proxypunishments.velocity.command.support.PlayerSuggestions;
import net.vanfek.proxypunishments.velocity.platform.VelocityActor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AltsCommand {

    private static final int PAGE_SIZE = 20;

    private AltsCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, ProxyServer proxy, String literal) {
        LiteralArgumentBuilder<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal(literal)
                .requires(source -> ctx.permissions().allows(VelocityActor.of(source), PermissionNodes.ALTS))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> PlayerSuggestions.matching(proxy, builder))
                        .executes(context -> execute(
                                ctx, VelocityActor.of(context.getSource()),
                                StringArgumentType.getString(context, "player")
                        )));

        return new BrigadierCommand(node);
    }

    private static int execute(CommandContext ctx, Actor actor, String playerName) {
        ctx.punishments().resolveUuid(playerName)
                .thenCompose(resolved -> resolved
                        .map(uuid -> print(ctx, actor, playerName, uuid))
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

    private static CompletableFuture<Void> print(CommandContext ctx, Actor actor, String playerName, UUID uuid) {
        return ctx.punishments().alts(uuid, PAGE_SIZE).thenAccept(alts -> {
            if (alts.isEmpty()) {
                Feedback.tell(ctx, actor, "alts.empty", Placeholders.of("player", playerName));

                return;
            }

            Feedback.tell(ctx, actor, "alts.header", Placeholders.of("player", playerName));

            renderEntries(ctx, actor, alts);
        });
    }

    private static void renderEntries(CommandContext ctx, Actor actor, List<AltView> alts) {
        for (AltView alt : alts) {
            Feedback.tell(
                    ctx, actor, "alts.entry",
                    Placeholders.of("alt", alt.login()), Placeholders.of("last_seen", alt.lastSeenAt())
            );
        }
    }

}
