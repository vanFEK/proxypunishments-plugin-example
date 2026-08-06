package net.vanfek.proxypunishments.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record ActivePunishmentView(
        long id, @NotNull UUID playerUuid, @Nullable String playerName,
        @NotNull PunishmentKind kind, @NotNull BanType type,
        @NotNull UUID issuedByUuid, @NotNull String issuedByName,
        @NotNull String reason, @NotNull Instant issuedAt, @Nullable Instant expiresAt
) {

    public boolean isPermanent() {
        return this.type == BanType.PERMANENT;
    }

    public boolean isBan() {
        return this.kind == PunishmentKind.BAN;
    }

    public boolean isMute() {
        return this.kind == PunishmentKind.MUTE;
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
