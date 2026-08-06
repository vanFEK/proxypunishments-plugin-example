package net.vanfek.proxybans.core.client;

import net.vanfek.proxybans.api.ActiveBanView;
import net.vanfek.proxybans.api.PunishmentHistoryEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface BansClient extends AutoCloseable {

    @NotNull CompletableFuture<Optional<ActiveBanView>> findActiveBan(@NotNull UUID uuid);

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

    @NotNull CompletableFuture<List<ActiveBanView>> listActiveBans(int limit, int offset);

    @NotNull CompletableFuture<List<PunishmentHistoryEntry>> history(@NotNull UUID uuid, int limit, int offset);

    @NotNull CompletableFuture<Optional<UUID>> lookup(@NotNull String login);

    @NotNull CompletableFuture<Void> rememberPlayer(@NotNull UUID uuid, @NotNull String login);

    @Override
    void close();

}
