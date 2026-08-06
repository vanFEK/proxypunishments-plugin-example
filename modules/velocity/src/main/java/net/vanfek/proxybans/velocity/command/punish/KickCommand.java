package net.vanfek.proxybans.velocity.command.punish;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.vanfek.proxybans.api.platform.Actor;
import net.vanfek.proxybans.core.i18n.Placeholders;
import net.vanfek.proxybans.core.permission.PermissionNodes;
import net.vanfek.proxybans.velocity.command.support.CommandContext;
import net.vanfek.proxybans.velocity.command.support.Feedback;
import net.vanfek.proxybans.velocity.platform.VelocityActor;

import java.util.Optional;
import java.util.UUID;

public final class KickCommand {

    private KickCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, ProxyServer proxy) {
        LiteralArgumentBuilder<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal("kick")
                .requires(source -> ctx.permissions().allows(VelocityActor.of(source), PermissionNodes.KICK))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("target", StringArgumentType.word())
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("reason", StringArgumentType.greedyString())
                                .executes(context -> execute(
                                        ctx, proxy, VelocityActor.of(context.getSource()),
                                        StringArgumentType.getString(context, "target"),
                                        StringArgumentType.getString(context, "reason")
                                ))));

        return new BrigadierCommand(node);
    }

    private static int execute(CommandContext ctx, ProxyServer proxy, Actor actor, String target, String reason) {
        Optional<Player> resolved = byUuid(proxy, target).or(() -> proxy.getPlayer(target));

        if (resolved.isEmpty()) {
            Feedback.tell(ctx, actor, "kick.not-online", Placeholders.of("target", target));

            return Command.SINGLE_SUCCESS;
        }

        Player player = resolved.get();

        ctx.punishments().invalidate(player.getUniqueId());
        player.disconnect(ctx.messages().render("kick.player-reason", Placeholders.of("reason", reason)));

        Feedback.tell(
                ctx, actor, "kick.done",
                Placeholders.of("target", player.getUsername()), Placeholders.of("reason", reason)
        );

        return Command.SINGLE_SUCCESS;
    }

    private static Optional<Player> byUuid(ProxyServer proxy, String value) {
        try {
            return proxy.getPlayer(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

}
