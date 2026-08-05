package net.vanfek.proxybans.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Who issued a punishment. The console has no UUID, so it gets the nil UUID — a stable, obviously
 * synthetic value the service can store without a nullable column.
 */
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
