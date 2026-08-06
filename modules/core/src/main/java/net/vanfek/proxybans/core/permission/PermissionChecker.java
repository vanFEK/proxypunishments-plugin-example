package net.vanfek.proxybans.core.permission;

import net.vanfek.proxybans.api.platform.Actor;
import org.jetbrains.annotations.NotNull;

public interface PermissionChecker {

    boolean hasPermission(@NotNull Actor actor, @NotNull String node);

    default boolean allows(@NotNull Actor actor, @NotNull String node) {
        return hasPermission(actor, node) || hasPermission(actor, PermissionNodes.ADMIN);
    }

}
