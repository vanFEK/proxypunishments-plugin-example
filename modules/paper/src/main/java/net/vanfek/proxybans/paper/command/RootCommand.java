package net.vanfek.proxybans.paper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.vanfek.proxybans.paper.command.lookup.HistoryCommand;
import net.vanfek.proxybans.paper.command.lookup.InfoCommand;
import net.vanfek.proxybans.paper.command.lookup.ListCommand;
import net.vanfek.proxybans.paper.command.mute.MuteCommand;
import net.vanfek.proxybans.paper.command.mute.TempMuteCommand;
import net.vanfek.proxybans.paper.command.mute.UnmuteCommand;
import net.vanfek.proxybans.paper.command.punish.BanCommand;
import net.vanfek.proxybans.paper.command.punish.KickCommand;
import net.vanfek.proxybans.paper.command.punish.TempBanCommand;
import net.vanfek.proxybans.paper.command.punish.UnbanCommand;
import net.vanfek.proxybans.paper.command.punish.WarnCommand;
import net.vanfek.proxybans.paper.command.support.CommandContext;
import net.vanfek.proxybans.paper.command.support.Feedback;
import net.vanfek.proxybans.paper.platform.PaperActor;
import org.bukkit.Server;

import java.util.function.Function;

public final class RootCommand {

    public static final String ROOT_LITERAL = "pb";
    public static final String ROOT_ALIAS = "proxybans";

    private RootCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(CommandContext ctx, Server server) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(ROOT_LITERAL)
                .executes(context -> {
                    Feedback.tell(ctx, PaperActor.of(context.getSource().getSender()), "root.help");

                    return Command.SINGLE_SUCCESS;
                });

        addFreshSubcommands(root, literal -> BanCommand.build(ctx, server, literal), "ban", "permban");
        addFreshSubcommands(root, literal -> TempBanCommand.build(ctx, server, literal), "tempban", "tban");
        addFreshSubcommands(root, literal -> UnbanCommand.build(ctx, literal), "unban", "pardon");
        addFreshSubcommands(root, literal -> MuteCommand.build(ctx, server, literal), "mute", "permmute");
        addFreshSubcommands(root, literal -> TempMuteCommand.build(ctx, server, literal), "tempmute", "tmute");
        addFreshSubcommands(root, literal -> UnmuteCommand.build(ctx, literal), "unmute", "unsilence");
        addFreshSubcommands(root, literal -> WarnCommand.build(ctx, server, literal), "warn", "caution");
        addFreshSubcommands(root, literal -> InfoCommand.build(ctx, server, literal), "info", "check");
        addFreshSubcommands(root, literal -> HistoryCommand.build(ctx, server, literal), "history", "log");
        addFreshSubcommands(root, literal -> ListCommand.build(ctx, literal), "list", "bans");

        root.then(KickCommand.build(ctx, server));

        return root.build();
    }

    private static void addFreshSubcommands(
            LiteralArgumentBuilder<CommandSourceStack> root,
            Function<String, LiteralCommandNode<CommandSourceStack>> factory, String... literals
    ) {
        for (String literal : literals) {
            root.then(factory.apply(literal));
        }
    }

}
