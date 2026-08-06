package net.vanfek.proxybans.core.client;

import net.vanfek.proxybans.api.ActivePunishmentView;
import net.vanfek.proxybans.api.PunishmentHistoryEntry;
import net.vanfek.proxybans.api.PunishmentKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PunishmentsClient extends AutoCloseable {

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

    @NotNull CompletableFuture<Optional<UUID>> lookup(@NotNull String login);

    @NotNull CompletableFuture<Void> rememberPlayer(@NotNull UUID uuid, @NotNull String login);

    @Override
    void close();

}
