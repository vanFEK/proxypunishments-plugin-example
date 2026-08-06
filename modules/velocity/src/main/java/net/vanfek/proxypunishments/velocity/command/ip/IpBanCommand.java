package net.vanfek.proxypunishments.velocity.command.ip;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.vanfek.proxypunishments.api.platform.Actor;
import net.vanfek.proxypunishments.core.command.PunishmentArguments;
import net.vanfek.proxypunishments.core.duration.DurationFormatter;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import net.vanfek.proxypunishments.core.permission.PermissionNodes;
import net.vanfek.proxypunishments.velocity.command.support.CommandContext;
import net.vanfek.proxypunishments.velocity.command.support.Feedback;
import net.vanfek.proxypunishments.velocity.command.support.IpTarget;
import net.vanfek.proxypunishments.velocity.command.support.PlayerSuggestions;
import net.vanfek.proxypunishments.velocity.platform.VelocityActor;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class IpBanCommand {

    private IpBanCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, ProxyServer proxy, String literal) {
        LiteralArgumentBuilder<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal(literal)
                .requires(source -> ctx.permissions().allows(VelocityActor.of(source), PermissionNodes.IPBAN))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("target", StringArgumentType.word())
                        .suggests((context, builder) -> PlayerSuggestions.matching(proxy, builder))
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("arguments", StringArgumentType.greedyString())
                                .executes(context -> execute(
                                        ctx, proxy, VelocityActor.of(context.getSource()),
                                        StringArgumentType.getString(context, "target"),
                                        StringArgumentType.getString(context, "arguments")
                                ))));

        return new BrigadierCommand(node);
    }

    private static int execute(CommandContext ctx, ProxyServer proxy, Actor actor, String target, String tail) {
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

        Optional<IpTarget> resolved = IpTarget.resolve(proxy, target);

        if (resolved.isEmpty()) {
            Feedback.tell(ctx, actor, "errors.unknown-address", Placeholders.of("target", target));

            return Command.SINGLE_SUCCESS;
        }

        issue(ctx, actor, resolved.get(), arguments.reason(), arguments.duration().duration())
                .exceptionally(throwable -> {
                    Feedback.reportFailure(ctx, actor, throwable);

                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Void> issue(
            CommandContext ctx, Actor actor,
            IpTarget target, String reason, Duration duration
    ) {
        return ctx.punishments()
                .issueIpBan(target.address(), target.playerUuid(), actor.uuid(), actor.name(), reason, duration)
                .thenAccept(ban -> {
                    String key = ban.isPermanent() ? "ipban.success-permanent" : "ipban.success-temporary";

                    Feedback.tell(
                            ctx, actor, key,
                            Placeholders.of("address", target.address()), Placeholders.of("reason", reason),
                            Placeholders.of("duration", duration == null ? "—" : DurationFormatter.human(duration))
                    );
                });
    }

}
