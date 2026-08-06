package net.vanfek.proxybans.core.permission;

import com.velocitypowered.api.command.CommandSource;
import org.jetbrains.annotations.NotNull;

public interface PermissionChecker {

    boolean hasPermission(@NotNull CommandSource source, @NotNull String node);

    default boolean allows(@NotNull CommandSource source, @NotNull String node) {
        return hasPermission(source, node) || hasPermission(source, PermissionNodes.ADMIN);
    }

}
