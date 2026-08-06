package net.vanfek.proxypunishments.velocity.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.vanfek.proxypunishments.velocity.command.lookup.HistoryCommand;
import net.vanfek.proxypunishments.velocity.command.lookup.InfoCommand;
import net.vanfek.proxypunishments.velocity.command.lookup.ListCommand;
import net.vanfek.proxypunishments.velocity.command.mute.MuteCommand;
import net.vanfek.proxypunishments.velocity.command.mute.TempMuteCommand;
import net.vanfek.proxypunishments.velocity.command.mute.UnmuteCommand;
import net.vanfek.proxypunishments.velocity.command.punish.BanCommand;
import net.vanfek.proxypunishments.velocity.command.punish.KickCommand;
import net.vanfek.proxypunishments.velocity.command.punish.TempBanCommand;
import net.vanfek.proxypunishments.velocity.command.punish.UnbanCommand;
import net.vanfek.proxypunishments.velocity.command.punish.WarnCommand;
import net.vanfek.proxypunishments.velocity.command.support.CommandContext;
import net.vanfek.proxypunishments.velocity.command.support.Feedback;
import net.vanfek.proxypunishments.velocity.platform.VelocityActor;

import java.util.function.Function;

public final class RootCommand {

    public static final String ROOT_LITERAL = "pp";
    public static final String ROOT_ALIAS = "proxypunishments";

    private RootCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, ProxyServer proxy) {
        LiteralArgumentBuilder<CommandSource> root = LiteralArgumentBuilder.<CommandSource>literal(ROOT_LITERAL)
                .executes(context -> {
                    Feedback.tell(ctx, VelocityActor.of(context.getSource()), "root.help");

                    return Command.SINGLE_SUCCESS;
                });

        addFreshSubcommands(root, literal -> BanCommand.build(ctx, proxy, literal), "ban", "permban");
        addFreshSubcommands(root, literal -> TempBanCommand.build(ctx, proxy, literal), "tempban", "tban");
        addFreshSubcommands(root, literal -> UnbanCommand.build(ctx, literal), "unban", "pardon");
        addFreshSubcommands(root, literal -> MuteCommand.build(ctx, proxy, literal), "mute", "permmute");
        addFreshSubcommands(root, literal -> TempMuteCommand.build(ctx, proxy, literal), "tempmute", "tmute");
        addFreshSubcommands(root, literal -> UnmuteCommand.build(ctx, literal), "unmute", "unsilence");
        addFreshSubcommands(root, literal -> WarnCommand.build(ctx, proxy, literal), "warn", "caution");
        addFreshSubcommands(root, literal -> InfoCommand.build(ctx, proxy, literal), "info", "check");
        addFreshSubcommands(root, literal -> HistoryCommand.build(ctx, proxy, literal), "history", "log");
        addFreshSubcommands(root, literal -> ListCommand.build(ctx, literal), "list", "bans");

        root.then(KickCommand.build(ctx, proxy).getNode());

        return new BrigadierCommand(root);
    }

    private static void addFreshSubcommands(
            LiteralArgumentBuilder<CommandSource> root,
            Function<String, BrigadierCommand> factory, String... literals
    ) {
        for (String literal : literals) {
            root.then(factory.apply(literal).getNode());
        }
    }

}
