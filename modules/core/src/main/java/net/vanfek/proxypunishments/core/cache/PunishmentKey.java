package net.vanfek.proxypunishments.core.cache;

import net.vanfek.proxypunishments.api.PunishmentKind;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record PunishmentKey(@NotNull UUID playerUuid, @NotNull PunishmentKind kind) implements PunishmentCacheKey {

    public static @NotNull PunishmentKey of(@NotNull UUID playerUuid, @NotNull PunishmentKind kind) {
        return new PunishmentKey(playerUuid, kind);
    }

}
