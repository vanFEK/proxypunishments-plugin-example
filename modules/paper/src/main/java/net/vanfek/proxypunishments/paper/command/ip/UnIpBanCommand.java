package net.vanfek.proxypunishments.paper.command.ip;

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
import net.vanfek.proxypunishments.paper.command.support.IpTarget;
import net.vanfek.proxypunishments.paper.platform.PaperActor;
import org.bukkit.Server;

import java.util.Optional;

public final class UnIpBanCommand {

    private UnIpBanCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(CommandContext ctx, Server server, String literal) {
        return Commands.literal(literal)
                .requires(source -> ctx.permissions().allows(PaperActor.of(source.getSender()), PermissionNodes.UNIPBAN))
                .then(Commands.argument("target", StringArgumentType.word())
                        .executes(context -> execute(
                                ctx, server, PaperActor.of(context.getSource().getSender()),
                                StringArgumentType.getString(context, "target"), ""
                        ))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> execute(
                                        ctx, server, PaperActor.of(context.getSource().getSender()),
                                        StringArgumentType.getString(context, "target"),
                                        StringArgumentType.getString(context, "reason")
                                ))))
                .build();
    }

    private static int execute(CommandContext ctx, Server server, Actor actor, String target, String reason) {
        Optional<IpTarget> resolved = IpTarget.resolve(server, target);

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
