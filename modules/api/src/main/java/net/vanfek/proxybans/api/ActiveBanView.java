package net.vanfek.proxybans.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable snapshot of the ban that is currently in force for a player.
 *
 * @param playerName last known name of the banned player, absent when the backing store has never seen the player
 */
public record ActiveBanView(
        long id,
        @NotNull UUID playerUuid,
        @Nullable String playerName,
        @NotNull UUID issuedByUuid,
        @NotNull String issuedByName,
        @NotNull String reason,
        @NotNull Instant issuedAt,
        @Nullable Instant expiresAt,
        @NotNull BanType type
) {

    public boolean isPermanent() {
        return this.type == BanType.PERMANENT;
    }

    public boolean isExpired(@NotNull Instant now) {
        return this.expiresAt != null && now.isAfter(this.expiresAt);
    }

    /**
     * @return the player name when known, otherwise the string form of the UUID, so callers always have something
     * printable without having to branch themselves
     */
    public @NotNull String displayName() {
        return this.playerName != null && !this.playerName.isBlank()
                ? this.playerName
                : this.playerUuid.toString();
    }

    public @NotNull Optional<Instant> expiry() {
        return Optional.ofNullable(this.expiresAt);
    }

}
