package net.vanfek.proxybans.paper.command.punish;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.vanfek.proxybans.api.platform.Actor;
import net.vanfek.proxybans.core.i18n.Placeholders;
import net.vanfek.proxybans.core.permission.PermissionNodes;
import net.vanfek.proxybans.paper.command.support.CommandContext;
import net.vanfek.proxybans.paper.command.support.Feedback;
import net.vanfek.proxybans.paper.platform.PaperActor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class KickCommand {

    private KickCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(CommandContext ctx, Server server) {
        return Commands.literal("kick")
                .requires(source -> ctx.permissions().allows(PaperActor.of(source.getSender()), PermissionNodes.KICK))
                .then(Commands.argument("target", StringArgumentType.word())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> execute(
                                        ctx, server, PaperActor.of(context.getSource().getSender()),
                                        StringArgumentType.getString(context, "target"),
                                        StringArgumentType.getString(context, "reason")
                                ))))
                .build();
    }

    private static int execute(CommandContext ctx, Server server, Actor actor, String target, String reason) {
        Player player = resolve(server, target);

        if (player == null) {
            Feedback.tell(ctx, actor, "kick.not-online", Placeholders.of("target", target));

            return Command.SINGLE_SUCCESS;
        }

        ctx.punishments().invalidate(player.getUniqueId());
        player.kick(ctx.messages().render("kick.player-reason", Placeholders.of("reason", reason)));

        Feedback.tell(
                ctx, actor, "kick.done",
                Placeholders.of("target", player.getName()), Placeholders.of("reason", reason)
        );

        return Command.SINGLE_SUCCESS;
    }

    private static @Nullable Player resolve(Server server, String target) {
        Player byUuid = byUuid(server, target);

        return byUuid != null ? byUuid : server.getPlayerExact(target);
    }

    private static @Nullable Player byUuid(Server server, String value) {
        try {
            return server.getPlayer(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

}
