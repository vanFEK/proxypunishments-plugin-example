package net.vanfek.proxypunishments.core.cache;

import org.jetbrains.annotations.NotNull;

public record IpBanKey(@NotNull String ipAddress) implements PunishmentCacheKey {

    public static @NotNull IpBanKey of(@NotNull String ipAddress) {
        return new IpBanKey(ipAddress);
    }

}
