package net.vanfek.proxypunishments.api;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.UUID;

public record AltView(@NotNull UUID uuid, @NotNull String login, @NotNull Instant lastSeenAt, boolean sharedIp) {
}
