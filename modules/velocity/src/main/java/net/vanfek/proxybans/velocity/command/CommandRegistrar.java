package net.vanfek.proxybans.velocity.command;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.proxy.ProxyServer;
import net.vanfek.proxybans.velocity.command.lookup.BanHistoryCommand;
import net.vanfek.proxybans.velocity.command.lookup.BanInfoCommand;
import net.vanfek.proxybans.velocity.command.lookup.BanListCommand;
import net.vanfek.proxybans.velocity.command.punish.BanCommand;
import net.vanfek.proxybans.velocity.command.punish.TempBanCommand;
import net.vanfek.proxybans.velocity.command.punish.UnbanCommand;
import net.vanfek.proxybans.velocity.command.support.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class CommandRegistrar {

    private final ProxyServer proxy;
    private final CommandContext context;
    private final boolean registerTopLevelAliases;
    private final Logger logger;

    public CommandRegistrar(
            @NotNull ProxyServer proxy, @NotNull CommandContext context,
            boolean registerTopLevelAliases, @NotNull Logger logger
    ) {
        this.proxy = proxy;
        this.context = context;
        this.registerTopLevelAliases = registerTopLevelAliases;
        this.logger = logger;
    }

    public void register(@NotNull Object plugin) {
        CommandManager manager = this.proxy.getCommandManager();

        registerRoot(plugin, manager);

        if (this.registerTopLevelAliases) {
            registerTopLevel(plugin, manager);
        }
    }

    private void registerRoot(Object plugin, CommandManager manager) {
        BrigadierCommand command = RootCommand.build(this.context, this.proxy);

        CommandMeta meta = manager.metaBuilder(RootCommand.ROOT_LITERAL)
                .aliases(RootCommand.ROOT_ALIAS)
                .plugin(plugin)
                .build();

        manager.register(meta, command);
    }

    private void registerTopLevel(Object plugin, CommandManager manager) {
        registerIfNameIsFree(
                plugin, manager,
                literal -> BanCommand.build(this.context, this.proxy, literal),
                "ban", List.of("permban", "blacklist", "banplayer", "punish")
        );

        registerIfNameIsFree(
                plugin, manager,
                literal -> TempBanCommand.build(this.context, this.proxy, literal),
                "tempban", List.of("tban", "timedban")
        );

        registerIfNameIsFree(
                plugin, manager,
                literal -> UnbanCommand.build(this.context, literal),
                "unban", List.of("pardon", "unblacklist")
        );

        registerIfNameIsFree(
                plugin, manager,
                literal -> BanInfoCommand.build(this.context, this.proxy, literal),
                "baninfo", List.of("checkban", "isbanned", "banstatus")
        );

        registerIfNameIsFree(
                plugin, manager,
                literal -> BanHistoryCommand.build(this.context, this.proxy, literal),
                "banhistory", List.of("banlog", "punishments")
        );

        registerIfNameIsFree(
                plugin, manager,
                literal -> BanListCommand.build(this.context, literal),
                "banlist", List.of("bans", "activebans")
        );
    }

    private void registerIfNameIsFree(
            Object plugin, CommandManager manager,
            Function<String, BrigadierCommand> factory,
            String primary, List<String> aliases
    ) {
        if (manager.hasCommand(primary)) {
            this.logger.warn(
                    "Command '/{}' is already registered by another plugin — skipping it, use /{} {} instead",
                    primary, RootCommand.ROOT_LITERAL, primary
            );

            return;
        }

        List<String> free = new ArrayList<>();

        for (String alias : aliases) {
            if (manager.hasCommand(alias)) {
                this.logger.warn("Alias '/{}' is already registered by another plugin — skipping it", alias);

                continue;
            }

            free.add(alias);
        }

        CommandMeta meta = manager.metaBuilder(primary)
                .aliases(free.toArray(String[]::new))
                .plugin(plugin)
                .build();

        manager.register(meta, factory.apply(primary));
    }

}
