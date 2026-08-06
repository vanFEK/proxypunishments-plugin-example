package net.vanfek.proxybans.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface BanService {

    @NotNull CompletableFuture<Optional<ActiveBanView>> findActiveBan(@NotNull UUID playerUuid);

    @NotNull CompletableFuture<ActiveBanView> issueBan(
            @NotNull UUID playerUuid,
            @NotNull UUID issuedByUuid, @NotNull String issuedByName,
            @NotNull String reason, @Nullable Duration duration
    );

    @NotNull CompletableFuture<Void> liftBan(
            @NotNull UUID playerUuid,
            @NotNull UUID liftedByUuid, @NotNull String liftedByName,
            @NotNull String reason
    );

    @NotNull CompletableFuture<List<PunishmentHistoryEntry>> history(@NotNull UUID playerUuid, int limit, int offset);

    @NotNull CompletableFuture<List<ActiveBanView>> listActive(int limit, int offset);

    @NotNull CompletableFuture<Optional<UUID>> resolveUuid(@NotNull String login);

    void invalidate(@NotNull UUID playerUuid);

}
