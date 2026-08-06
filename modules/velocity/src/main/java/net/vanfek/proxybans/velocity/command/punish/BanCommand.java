package net.vanfek.proxybans.velocity.command.punish;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.vanfek.proxybans.api.PunishmentKind;
import net.vanfek.proxybans.api.platform.Actor;
import net.vanfek.proxybans.core.command.PunishmentArguments;
import net.vanfek.proxybans.core.duration.DurationFormatter;
import net.vanfek.proxybans.core.i18n.Placeholders;
import net.vanfek.proxybans.core.permission.PermissionNodes;
import net.vanfek.proxybans.velocity.command.support.CommandContext;
import net.vanfek.proxybans.velocity.command.support.Feedback;
import net.vanfek.proxybans.velocity.command.support.PlayerSuggestions;
import net.vanfek.proxybans.velocity.platform.VelocityActor;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BanCommand {

    private BanCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, ProxyServer proxy, String literal) {
        LiteralArgumentBuilder<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal(literal)
                .requires(source -> ctx.permissions().allows(VelocityActor.of(source), PermissionNodes.BAN))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> PlayerSuggestions.matching(proxy, builder))
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("arguments", StringArgumentType.greedyString())
                                .executes(context -> execute(
                                        ctx, VelocityActor.of(context.getSource()),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "arguments")
                                ))));

        return new BrigadierCommand(node);
    }

    private static int execute(CommandContext ctx, Actor actor, String playerName, String tail) {
        PunishmentArguments arguments;

        try {
            arguments = PunishmentArguments.parse(tail);
        } catch (IllegalArgumentException ex) {
            Feedback.tell(ctx, actor, "errors.bad-duration", Placeholders.of("value", tail));

            return Command.SINGLE_SUCCESS;
        }

        if (!arguments.hasReason()) {
            Feedback.tell(ctx, actor, "errors.reason-required");

            return Command.SINGLE_SUCCESS;
        }

        Duration duration = arguments.duration().duration();

        ctx.punishments().resolveUuid(playerName)
                .thenCompose(resolved -> resolved
                        .map(uuid -> issue(ctx, actor, playerName, uuid, arguments.reason(), duration))
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
            CommandContext ctx, Actor actor,
            String playerName, UUID uuid,
            String reason, Duration duration
    ) {
        return ctx.punishments().issue(uuid, PunishmentKind.BAN, actor.uuid(), actor.name(), reason, duration)
                .thenAccept(ban -> {
                    String key = ban.isPermanent() ? "ban.success-permanent" : "ban.success-temporary";

                    Feedback.tell(
                            ctx, actor, key,
                            Placeholders.of("player", playerName), Placeholders.of("reason", reason),
                            Placeholders.of("duration", duration == null ? "—" : DurationFormatter.human(duration))
                    );
                });
    }

}
