package net.vanfek.proxypunishments.core.permission;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ContextManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import net.vanfek.proxypunishments.api.platform.Actor;
import org.jetbrains.annotations.NotNull;

public final class LuckPermsPermissionChecker implements PermissionChecker {

    private final LuckPerms luckPerms;
    private final PermissionChecker fallback;

    public LuckPermsPermissionChecker(@NotNull LuckPerms luckPerms, @NotNull PermissionChecker fallback) {
        this.luckPerms = luckPerms;
        this.fallback = fallback;
    }

    @Override
    public boolean hasPermission(@NotNull Actor actor, @NotNull String node) {
        if (!actor.isPlayer()) {
            return this.fallback.hasPermission(actor, node);
        }

        User user = this.luckPerms.getUserManager().getUser(actor.uuid());

        // An uncached user answers UNDEFINED to everything; loading it here would block the caller, so ask the platform instead.
        if (user == null) {
            return this.fallback.hasPermission(actor, node);
        }

        Tristate state = user.getCachedData()
                .getPermissionData(queryOptionsFor(user))
                .checkPermission(node);

        return switch (state) {
            case TRUE -> true;
            case FALSE -> false;
            case UNDEFINED -> this.fallback.hasPermission(actor, node);
        };
    }

    private QueryOptions queryOptionsFor(User user) {
        ContextManager contexts = this.luckPerms.getContextManager();

        return contexts.getQueryOptions(user).orElseGet(contexts::getStaticQueryOptions);
    }

}
