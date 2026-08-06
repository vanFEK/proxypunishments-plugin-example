package net.vanfek.proxypunishments.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record PunishmentHistoryEntry(
        @NotNull UUID playerUuid, @NotNull UUID issuedByUuid, @NotNull String issuedByName,
        @NotNull Action action, @NotNull String reason,
        @NotNull Instant happenedAt, @Nullable Instant expiresAt
) {

    public @NotNull Optional<Instant> expiry() {
        return Optional.ofNullable(this.expiresAt);
    }

    public enum Action {

        BAN,
        PERMBAN,
        UNBAN,
        MUTE,
        PERMMUTE,
        UNMUTE,
        WARN

    }

}
