package net.vanfek.proxypunishments.velocity.command.support;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.vanfek.proxypunishments.api.platform.Actor;
import net.vanfek.proxypunishments.core.address.IpAddresses;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

/** A bare address carries the nil UUID; a player name resolves to the address they are connected from. */
public record IpTarget(@NotNull String address, @NotNull UUID playerUuid) {

    public static @NotNull Optional<IpTarget> resolve(@NotNull ProxyServer proxy, @NotNull String token) {
        Optional<String> literal = IpAddresses.parse(token);

        if (literal.isPresent()) {
            return literal.map(address -> new IpTarget(address, Actor.CONSOLE_UUID));
        }

        return proxy.getPlayer(token).flatMap(IpTarget::of);
    }

    private static Optional<IpTarget> of(Player player) {
        InetSocketAddress remote = player.getRemoteAddress();

        if (remote == null || remote.getAddress() == null) {
            return Optional.empty();
        }

        return IpAddresses.parse(remote.getAddress().getHostAddress())
                .map(address -> new IpTarget(address, player.getUniqueId()));
    }

}
