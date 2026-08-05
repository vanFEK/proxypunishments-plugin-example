package net.vanfek.proxybans.velocity.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.vanfek.proxybans.core.i18n.Placeholders;
import net.vanfek.proxybans.core.permission.PermissionNodes;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code /pb kick <uuid|name> <reason...>}.
 *
 * <p>Exists mainly for the website to call over RCON right after it writes a ban of its own: the ban
 * itself already lives in the store, this only drops the player who is online at that moment and
 * clears the cached "not banned" answer so the next login is re-checked.
 */
public final class KickCommand {

    private KickCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, ProxyServer proxy) {
        LiteralArgumentBuilder<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal("kick")
                .requires(source -> ctx.permissions().allows(source, PermissionNodes.KICK))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("target", StringArgumentType.word())
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("reason", StringArgumentType.greedyString())
                                .executes(context -> execute(
                                        ctx,
                                        proxy,
                                        context.getSource(),
                                        StringArgumentType.getString(context, "target"),
                                        StringArgumentType.getString(context, "reason")
                                ))));

        return new BrigadierCommand(node);
    }

    private static int execute(CommandContext ctx, ProxyServer proxy, CommandSource source, String target, String reason) {
        Optional<Player> resolved = byUuid(proxy, target).or(() -> proxy.getPlayer(target));

        if (resolved.isEmpty()) {
            CommandUtils.tell(ctx, source, "kick.not-online", Placeholders.of("target", target));

            return Command.SINGLE_SUCCESS;
        }

        Player player = resolved.get();

        ctx.bans().invalidate(player.getUniqueId());
        player.disconnect(ctx.messages().render("kick.player-reason", Placeholders.of("reason", reason)));

        CommandUtils.tell(
                ctx,
                source,
                "kick.done",
                Placeholders.of("target", player.getUsername()),
                Placeholders.of("reason", reason)
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
