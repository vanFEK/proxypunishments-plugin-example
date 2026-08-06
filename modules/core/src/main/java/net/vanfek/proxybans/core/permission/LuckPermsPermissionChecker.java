package net.vanfek.proxybans.core.permission;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.util.Tristate;
import org.jetbrains.annotations.NotNull;

/** Goes through the player adapter, not the user manager, which returns {@code null} for uncached users. */
public final class LuckPermsPermissionChecker implements PermissionChecker {

    private final LuckPerms luckPerms;
    private final PermissionChecker fallback;

    public LuckPermsPermissionChecker(@NotNull LuckPerms luckPerms, @NotNull PermissionChecker fallback) {
        this.luckPerms = luckPerms;
        this.fallback = fallback;
    }

    @Override
    public boolean hasPermission(@NotNull CommandSource source, @NotNull String node) {
        if (!(source instanceof Player player)) {
            return this.fallback.hasPermission(source, node);
        }

        Tristate state = this.luckPerms.getPlayerAdapter(Player.class)
                .getPermissionData(player)
                .checkPermission(node);

        return switch (state) {
            case TRUE -> true;
            case FALSE -> false;
            case UNDEFINED -> this.fallback.hasPermission(source, node);
        };
    }

}
