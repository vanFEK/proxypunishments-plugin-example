package net.vanfek.proxypunishments.velocity.command.ip;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.vanfek.proxypunishments.api.platform.Actor;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import net.vanfek.proxypunishments.core.permission.PermissionNodes;
import net.vanfek.proxypunishments.velocity.command.support.CommandContext;
import net.vanfek.proxypunishments.velocity.command.support.Feedback;
import net.vanfek.proxypunishments.velocity.command.support.IpTarget;
import net.vanfek.proxypunishments.velocity.platform.VelocityActor;

import java.util.Optional;

public final class UnIpBanCommand {

    private UnIpBanCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, ProxyServer proxy, String literal) {
        LiteralArgumentBuilder<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal(literal)
                .requires(source -> ctx.permissions().allows(VelocityActor.of(source), PermissionNodes.UNIPBAN))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("target", StringArgumentType.word())
                        .executes(context -> execute(
                                ctx, proxy, VelocityActor.of(context.getSource()),
                                StringArgumentType.getString(context, "target"), ""
                        ))
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("reason", StringArgumentType.greedyString())
                                .executes(context -> execute(
                                        ctx, proxy, VelocityActor.of(context.getSource()),
                                        StringArgumentType.getString(context, "target"),
                                        StringArgumentType.getString(context, "reason")
                                ))));

        return new BrigadierCommand(node);
    }

    private static int execute(CommandContext ctx, ProxyServer proxy, Actor actor, String target, String reason) {
        Optional<IpTarget> resolved = IpTarget.resolve(proxy, target);

        if (resolved.isEmpty()) {
            Feedback.tell(ctx, actor, "errors.unknown-address", Placeholders.of("target", target));

            return Command.SINGLE_SUCCESS;
        }

        String address = resolved.get().address();

        ctx.punishments().liftIpBan(address, actor.uuid(), actor.name(), reason)
                .thenRun(() -> Feedback.tell(
                        ctx, actor, "unipban.success",
                        Placeholders.of("address", address),
                        Placeholders.of("reason", reason.isBlank() ? "—" : reason)
                ))
                .exceptionally(throwable -> {
                    Feedback.reportFailure(ctx, actor, throwable);

                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

}
