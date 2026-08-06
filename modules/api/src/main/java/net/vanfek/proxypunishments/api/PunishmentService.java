package net.vanfek.proxypunishments.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PunishmentService {

    @NotNull CompletableFuture<Optional<ActivePunishmentView>> findActive(@NotNull UUID playerUuid, @NotNull PunishmentKind kind);

    @NotNull CompletableFuture<ActivePunishmentView> issue(
            @NotNull UUID playerUuid, @NotNull PunishmentKind kind,
            @NotNull UUID issuedByUuid, @NotNull String issuedByName,
            @NotNull String reason, @Nullable Duration duration
    );

    @NotNull CompletableFuture<Void> lift(
            @NotNull UUID playerUuid, @NotNull PunishmentKind kind,
            @NotNull UUID liftedByUuid, @NotNull String liftedByName, @NotNull String reason
    );

    @NotNull CompletableFuture<PunishmentHistoryEntry> warn(
            @NotNull UUID playerUuid, @NotNull UUID issuedByUuid,
            @NotNull String issuedByName, @NotNull String reason
    );

    @NotNull CompletableFuture<List<ActivePunishmentView>> listActive(@NotNull PunishmentKind kind, int limit, int offset);

    @NotNull CompletableFuture<List<PunishmentHistoryEntry>> history(@NotNull UUID playerUuid, int limit, int offset);

    @NotNull CompletableFuture<Optional<UUID>> resolveUuid(@NotNull String login);

    void invalidate(@NotNull UUID playerUuid);

    void invalidate(@NotNull UUID playerUuid, @NotNull PunishmentKind kind);

}
