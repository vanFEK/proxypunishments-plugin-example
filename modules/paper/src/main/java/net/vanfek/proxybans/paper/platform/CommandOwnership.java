package net.vanfek.proxybans.paper.platform;

import net.vanfek.proxybans.core.config.CommandsMode;
import org.bukkit.Server;
import org.jetbrains.annotations.NotNull;

public final class CommandOwnership {

    private CommandOwnership() {
    }

    public static boolean behindProxy(@NotNull Server server) {
        return server.getServerConfig().isProxyEnabled();
    }

    public static boolean registersCommands(@NotNull CommandsMode mode, boolean behindProxy) {
        return switch (mode) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> !behindProxy;
        };
    }

}
