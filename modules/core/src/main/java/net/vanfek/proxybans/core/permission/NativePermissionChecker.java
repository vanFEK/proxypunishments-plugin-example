package net.vanfek.proxybans.core.permission;

import com.velocitypowered.api.command.CommandSource;
import org.jetbrains.annotations.NotNull;

/**
 * Velocity's built-in check. Without a permission plugin it grants nothing to players and everything
 * to the console, which is the safe default: a misconfigured server locks staff out rather than
 * handing {@code /ban} to everyone.
 */
public final class NativePermissionChecker implements PermissionChecker {

    @Override
    public boolean hasPermission(@NotNull CommandSource source, @NotNull String node) {
        return source.hasPermission(node);
    }

}
