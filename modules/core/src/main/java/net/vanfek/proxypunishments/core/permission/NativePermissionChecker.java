package net.vanfek.proxypunishments.core.permission;

import net.vanfek.proxypunishments.api.platform.Actor;
import org.jetbrains.annotations.NotNull;

public final class NativePermissionChecker implements PermissionChecker {

    @Override
    public boolean hasPermission(@NotNull Actor actor, @NotNull String node) {
        return actor.hasPermission(node);
    }

}
