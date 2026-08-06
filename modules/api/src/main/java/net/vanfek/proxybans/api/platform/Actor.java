package net.vanfek.proxybans.api.platform;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface Actor {

    UUID CONSOLE_UUID = new UUID(0L, 0L);

    @NotNull UUID uuid();

    @NotNull String name();

    boolean isPlayer();

    boolean hasPermission(@NotNull String node);

    void send(@NotNull Component message);

}
