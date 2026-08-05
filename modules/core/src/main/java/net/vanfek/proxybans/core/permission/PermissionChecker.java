package net.vanfek.proxybans.core.permission;

import com.velocitypowered.api.command.CommandSource;
import org.jetbrains.annotations.NotNull;

public interface PermissionChecker {

    /**
     * Checks exactly the node given, without any aggregation.
     */
    boolean hasPermission(@NotNull CommandSource source, @NotNull String node);

    /**
     * Checks the node, then falls back to {@link PermissionNodes#ADMIN}. Commands should call this
     * rather than {@link #hasPermission} so the aggregate admin node actually does something.
     */
    default boolean allows(@NotNull CommandSource source, @NotNull String node) {
        return hasPermission(source, node) || hasPermission(source, PermissionNodes.ADMIN);
    }

}
