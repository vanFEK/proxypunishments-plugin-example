package net.vanfek.proxybans.velocity.command.support;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record Issuer(@NotNull UUID uuid, @NotNull String name) {

    public static final UUID CONSOLE_UUID = new UUID(0L, 0L);
    private static final String CONSOLE_NAME = "Console";

    public static @NotNull Issuer of(@NotNull CommandSource source) {
        if (source instanceof Player player) {
            return new Issuer(player.getUniqueId(), player.getUsername());
        }

        return new Issuer(CONSOLE_UUID, CONSOLE_NAME);
    }

}
