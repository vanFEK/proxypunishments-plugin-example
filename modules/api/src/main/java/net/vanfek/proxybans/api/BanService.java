package net.vanfek.proxybans.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public entry point other plugins may depend on. Every method is asynchronous: nothing here may
 * be called from a thread that must not block, and no implementation is allowed to block internally.
 */
public interface BanService {

    @NotNull CompletableFuture<Optional<ActiveBanView>> findActiveBan(@NotNull UUID playerUuid);

    /**
     * @param duration {@code null} issues a permanent ban
     */
    @NotNull CompletableFuture<ActiveBanView> issueBan(
            @NotNull UUID playerUuid,
            @NotNull UUID issuedByUuid,
            @NotNull String issuedByName,
            @NotNull String reason,
            @Nullable Duration duration
    );

    @NotNull CompletableFuture<Void> liftBan(
            @NotNull UUID playerUuid,
            @NotNull UUID liftedByUuid,
            @NotNull String liftedByName,
            @NotNull String reason
    );

    @NotNull CompletableFuture<List<PunishmentHistoryEntry>> history(@NotNull UUID playerUuid, int limit, int offset);

    @NotNull CompletableFuture<List<ActiveBanView>> listActive(int limit, int offset);

    @NotNull CompletableFuture<Optional<UUID>> resolveUuid(@NotNull String login);

    /**
     * Drops any cached decision for the player, so the next lookup goes to the backing service.
     */
    void invalidate(@NotNull UUID playerUuid);

}
