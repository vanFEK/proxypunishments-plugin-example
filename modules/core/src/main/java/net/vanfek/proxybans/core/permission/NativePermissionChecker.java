package net.vanfek.proxybans.core.permission;

import com.velocitypowered.api.command.CommandSource;
import org.jetbrains.annotations.NotNull;

public final class NativePermissionChecker implements PermissionChecker {

    @Override
    public boolean hasPermission(@NotNull CommandSource source, @NotNull String node) {
        return source.hasPermission(node);
    }

}
