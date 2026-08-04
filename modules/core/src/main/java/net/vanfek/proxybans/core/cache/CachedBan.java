package net.vanfek.proxybans.core.cache;

import net.vanfek.proxybans.api.ActiveBanView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;

/**
 * A cached answer to "is this player banned right now?".
 *
 * <p>"No ban" is cached just as deliberately as "banned" — otherwise every login of an innocent
 * player would still hit the service.
 *
 * @param ban {@code null} means the service answered "not banned"
 */
public record CachedBan(@Nullable ActiveBanView ban, @NotNull Instant cachedAt) {

    public static @NotNull CachedBan present(@NotNull ActiveBanView ban, @NotNull Instant now) {
        return new CachedBan(ban, now);
    }

    public static @NotNull CachedBan absent(@NotNull Instant now) {
        return new CachedBan(null, now);
    }

    public @NotNull Optional<ActiveBanView> asOptional() {
        return Optional.ofNullable(this.ban);
    }

}
