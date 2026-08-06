package net.vanfek.proxybans.paper.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.vanfek.proxybans.paper.command.lookup.HistoryCommand;
import net.vanfek.proxybans.paper.command.lookup.InfoCommand;
import net.vanfek.proxybans.paper.command.lookup.ListCommand;
import net.vanfek.proxybans.paper.command.mute.MuteCommand;
import net.vanfek.proxybans.paper.command.mute.TempMuteCommand;
import net.vanfek.proxybans.paper.command.mute.UnmuteCommand;
import net.vanfek.proxybans.paper.command.punish.BanCommand;
import net.vanfek.proxybans.paper.command.punish.TempBanCommand;
import net.vanfek.proxybans.paper.command.punish.UnbanCommand;
import net.vanfek.proxybans.paper.command.punish.WarnCommand;
import net.vanfek.proxybans.paper.command.support.CommandContext;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class CommandRegistrar {

    private static final String DESCRIPTION = "ProxyBans punishment command";

    private final Server server;
    private final CommandContext context;
    private final boolean registerTopLevelAliases;
    private final Logger logger;

    public CommandRegistrar(
            @NotNull Server server, @NotNull CommandContext context,
            boolean registerTopLevelAliases, @NotNull Logger logger
    ) {
        this.server = server;
        this.context = context;
        this.registerTopLevelAliases = registerTopLevelAliases;
        this.logger = logger;
    }

    public void register(@NotNull LifecycleEventManager<Plugin> lifecycle) {
        lifecycle.registerEventHandler(LifecycleEvents.COMMANDS, event -> registerAll(event.registrar()));
    }

    private void registerAll(Commands commands) {
        registerRoot(commands);

        if (this.registerTopLevelAliases) {
            registerBans(commands);
            registerMutes(commands);
            registerLookups(commands);
        }
    }

    private void registerRoot(Commands commands) {
        commands.register(RootCommand.build(this.context, this.server), DESCRIPTION, List.of(RootCommand.ROOT_ALIAS));
    }

    private void registerBans(Commands commands) {
        registerIfNameIsFree(
                commands, literal -> BanCommand.build(this.context, this.server, literal),
                "ban", List.of("permban", "blacklist", "banplayer", "punish")
        );

        registerIfNameIsFree(
                commands, literal -> TempBanCommand.build(this.context, this.server, literal),
                "tempban", List.of("tban", "timedban")
        );

        registerIfNameIsFree(
                commands, literal -> UnbanCommand.build(this.context, literal),
                "unban", List.of("pardon", "unblacklist")
        );
    }

    private void registerMutes(Commands commands) {
        registerIfNameIsFree(
                commands, literal -> MuteCommand.build(this.context, this.server, literal),
                "mute", List.of("permmute", "silence", "muteplayer")
        );

        registerIfNameIsFree(
                commands, literal -> TempMuteCommand.build(this.context, this.server, literal),
                "tempmute", List.of("tmute", "timedmute")
        );

        registerIfNameIsFree(
                commands, literal -> UnmuteCommand.build(this.context, literal),
                "unmute", List.of("unsilence", "pardonmute")
        );

        registerIfNameIsFree(
                commands, literal -> WarnCommand.build(this.context, this.server, literal),
                "warn", List.of("caution", "warnplayer")
        );
    }

    private void registerLookups(Commands commands) {
        registerIfNameIsFree(
                commands, literal -> InfoCommand.build(this.context, this.server, literal),
                "baninfo", List.of("checkban", "isbanned", "banstatus", "punishmentinfo")
        );

        registerIfNameIsFree(
                commands, literal -> HistoryCommand.build(this.context, this.server, literal),
                "banhistory", List.of("banlog", "punishments")
        );

        registerIfNameIsFree(
                commands, literal -> ListCommand.build(this.context, literal),
                "banlist", List.of("bans", "activebans", "punishmentlist")
        );
    }

    private void registerIfNameIsFree(
            Commands commands, Function<String, LiteralCommandNode<CommandSourceStack>> factory,
            String primary, List<String> aliases
    ) {
        Set<String> claimed = commands.register(factory.apply(primary), DESCRIPTION, aliases);

        if (claimed.contains(primary)) {
            return;
        }

        this.logger.warn(
                "Command '/{}' is already registered by another plugin — skipping it, use /{} {} instead",
                primary, RootCommand.ROOT_LITERAL, primary
        );
    }

}
