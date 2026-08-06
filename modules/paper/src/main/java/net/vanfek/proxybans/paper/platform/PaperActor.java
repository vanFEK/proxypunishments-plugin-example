package net.vanfek.proxybans.paper.platform;

import net.kyori.adventure.text.Component;
import net.vanfek.proxybans.api.platform.Actor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class PaperActor implements Actor {

    private static final String CONSOLE_NAME = "Console";

    private final CommandSender sender;
    private final Player player;

    private PaperActor(@NotNull CommandSender sender, @Nullable Player player) {
        this.sender = sender;
        this.player = player;
    }

    public static @NotNull PaperActor of(@NotNull CommandSender sender) {
        return new PaperActor(sender, sender instanceof Player player ? player : null);
    }

    public static @NotNull PaperActor of(@NotNull Player player) {
        return new PaperActor(player, player);
    }

    @Override
    public @NotNull UUID uuid() {
        return this.player == null ? CONSOLE_UUID : this.player.getUniqueId();
    }

    @Override
    public @NotNull String name() {
        return this.player == null ? CONSOLE_NAME : this.player.getName();
    }

    @Override
    public boolean isPlayer() {
        return this.player != null;
    }

    @Override
    public boolean hasPermission(@NotNull String node) {
        return this.sender.hasPermission(node);
    }

    @Override
    public void send(@NotNull Component message) {
        this.sender.sendMessage(message);
    }

}
