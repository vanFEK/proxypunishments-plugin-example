package net.vanfek.proxybans.core.cache;

import net.vanfek.proxybans.api.ActivePunishmentView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;

public record CachedPunishment(@Nullable ActivePunishmentView punishment, @NotNull Instant cachedAt) {

    public static @NotNull CachedPunishment present(@NotNull ActivePunishmentView punishment, @NotNull Instant now) {
        return new CachedPunishment(punishment, now);
    }

    public static @NotNull CachedPunishment absent(@NotNull Instant now) {
        return new CachedPunishment(null, now);
    }

    public @NotNull Optional<ActivePunishmentView> asOptional() {
        return Optional.ofNullable(this.punishment);
    }

}
