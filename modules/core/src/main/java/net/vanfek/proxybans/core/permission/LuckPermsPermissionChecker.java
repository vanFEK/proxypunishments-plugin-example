package net.vanfek.proxybans.core.permission;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.util.Tristate;
import org.jetbrains.annotations.NotNull;

/**
 * Delegates to LuckPerms for online players.
 *
 * <p>Uses the player adapter rather than {@code getUserManager().getUser(uuid)}: the user manager
 * returns {@code null} whenever the user is not currently held in LuckPerms' cache, which silently
 * downgraded staff to the native checker — and therefore to "no permissions" — at exactly the wrong
 * moments.
 */
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
