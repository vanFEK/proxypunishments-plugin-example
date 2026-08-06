package net.vanfek.proxypunishments.core.invalidation;

import net.vanfek.proxypunishments.api.PunishmentKind;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

public record InvalidationMessage(@NotNull String target, @NotNull PunishmentKind kind, @NotNull String origin) {

    public boolean targetsAddress() {
        return this.kind == PunishmentKind.IP_BAN;
    }

    public @NotNull Optional<UUID> playerUuid() {
        try {
            return Optional.of(UUID.fromString(this.target));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

}
