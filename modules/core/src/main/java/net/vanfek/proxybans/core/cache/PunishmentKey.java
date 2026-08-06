package net.vanfek.proxybans.core.cache;

import net.vanfek.proxybans.api.PunishmentKind;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record PunishmentKey(@NotNull UUID playerUuid, @NotNull PunishmentKind kind) {

    public static @NotNull PunishmentKey of(@NotNull UUID playerUuid, @NotNull PunishmentKind kind) {
        return new PunishmentKey(playerUuid, kind);
    }

}
