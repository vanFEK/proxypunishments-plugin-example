package net.vanfek.proxypunishments.paper.command.ip;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.vanfek.proxypunishments.api.platform.Actor;
import net.vanfek.proxypunishments.core.command.PunishmentArguments;
import net.vanfek.proxypunishments.core.duration.DurationFormatter;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import net.vanfek.proxypunishments.core.permission.PermissionNodes;
import net.vanfek.proxypunishments.paper.command.support.CommandContext;
import net.vanfek.proxypunishments.paper.command.support.Feedback;
import net.vanfek.proxypunishments.paper.command.support.IpTarget;
import net.vanfek.proxypunishments.paper.command.support.PlayerSuggestions;
import net.vanfek.proxypunishments.paper.platform.PaperActor;
import org.bukkit.Server;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class IpBanCommand {

    private IpBanCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(CommandContext ctx, Server server, String literal) {
        return Commands.literal(literal)
                .requires(source -> ctx.permissions().allows(PaperActor.of(source.getSender()), PermissionNodes.IPBAN))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((context, builder) -> PlayerSuggestions.matching(server, builder))
                        .then(Commands.argument("arguments", StringArgumentType.greedyString())
                                .executes(context -> execute(
                                        ctx, server, PaperActor.of(context.getSource().getSender()),
                                        StringArgumentType.getString(context, "target"),
                                        StringArgumentType.getString(context, "arguments")
                                ))))
                .build();
    }

    private static int execute(CommandContext ctx, Server server, Actor actor, String target, String tail) {
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

        Optional<IpTarget> resolved = IpTarget.resolve(server, target);

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
