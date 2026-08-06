package net.vanfek.proxybans.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record ActiveBanView(
        long id,
        @NotNull UUID playerUuid, @Nullable String playerName,
        @NotNull UUID issuedByUuid, @NotNull String issuedByName,
        @NotNull String reason,
        @NotNull Instant issuedAt, @Nullable Instant expiresAt, @NotNull BanType type
) {

    public boolean isPermanent() {
        return this.type == BanType.PERMANENT;
    }

    public boolean isExpired(@NotNull Instant now) {
        return this.expiresAt != null && now.isAfter(this.expiresAt);
    }

    public @NotNull String displayName() {
        return this.playerName != null && !this.playerName.isBlank()
                ? this.playerName
                : this.playerUuid.toString();
    }

    public @NotNull Optional<Instant> expiry() {
        return Optional.ofNullable(this.expiresAt);
    }

}
