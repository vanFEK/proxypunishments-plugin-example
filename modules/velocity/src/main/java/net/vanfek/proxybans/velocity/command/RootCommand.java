package net.vanfek.proxybans.velocity.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.function.Function;

/**
 * {@code /pb <subcommand>}.
 *
 * <p>Each alias gets its own freshly built subtree rather than a copy of another node's children.
 * Sharing Brigadier nodes between trees couples their requirements and redirects in ways that only
 * surface as missing subcommands at runtime.
 */
public final class RootCommand {

    public static final String ROOT_LITERAL = "pb";
    public static final String ROOT_ALIAS = "proxybans";

    private RootCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, ProxyServer proxy) {
        LiteralArgumentBuilder<CommandSource> root = LiteralArgumentBuilder.<CommandSource>literal(ROOT_LITERAL)
                .executes(context -> {
                    CommandUtils.tell(ctx, context.getSource(), "root.help");

                    return Command.SINGLE_SUCCESS;
                });

        addSubcommands(root, literal -> BanCommand.build(ctx, proxy, literal), "ban", "permban");
        addSubcommands(root, literal -> TempBanCommand.build(ctx, proxy, literal), "tempban", "tban");
        addSubcommands(root, literal -> UnbanCommand.build(ctx, literal), "unban", "pardon");
        addSubcommands(root, literal -> BanInfoCommand.build(ctx, proxy, literal), "info", "check");
        addSubcommands(root, literal -> BanHistoryCommand.build(ctx, proxy, literal), "history", "log");
        addSubcommands(root, literal -> BanListCommand.build(ctx, literal), "list", "bans");

        root.then(KickCommand.build(ctx, proxy).getNode());

        return new BrigadierCommand(root);
    }

    private static void addSubcommands(
            LiteralArgumentBuilder<CommandSource> root,
            Function<String, BrigadierCommand> factory,
            String... literals
    ) {
        for (String literal : literals) {
            root.then(factory.apply(literal).getNode());
        }
    }

}
