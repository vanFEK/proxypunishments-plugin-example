package net.vanfek.proxybans.velocity.platform;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.vanfek.proxybans.api.platform.Actor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class VelocityActor implements Actor {

    private static final String CONSOLE_NAME = "Console";

    private final CommandSource source;
    private final Player player;

    private VelocityActor(@NotNull CommandSource source, @Nullable Player player) {
        this.source = source;
        this.player = player;
    }

    public static @NotNull VelocityActor of(@NotNull CommandSource source) {
        return new VelocityActor(source, source instanceof Player player ? player : null);
    }

    public static @NotNull VelocityActor of(@NotNull Player player) {
        return new VelocityActor(player, player);
    }

    @Override
    public @NotNull UUID uuid() {
        return this.player == null ? CONSOLE_UUID : this.player.getUniqueId();
    }

    @Override
    public @NotNull String name() {
        return this.player == null ? CONSOLE_NAME : this.player.getUsername();
    }

    @Override
    public boolean isPlayer() {
        return this.player != null;
    }

    @Override
    public boolean hasPermission(@NotNull String node) {
        return this.source.hasPermission(node);
    }

    @Override
    public void send(@NotNull Component message) {
        this.source.sendMessage(message);
    }

}
