package net.vanfek.proxypunishments.core;

import net.vanfek.proxypunishments.api.ActivePunishmentView;
import net.vanfek.proxypunishments.api.PunishmentHistoryEntry;
import net.vanfek.proxypunishments.api.PunishmentKind;
import net.vanfek.proxypunishments.api.PunishmentService;
import net.vanfek.proxypunishments.core.cache.CachedPunishment;
import net.vanfek.proxypunishments.core.cache.PunishmentCache;
import net.vanfek.proxypunishments.core.client.PunishmentsClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PunishmentServiceImpl implements PunishmentService, AutoCloseable {

    private final PunishmentsClient http;
    private final PunishmentCache cache;

    public PunishmentServiceImpl(@NotNull PunishmentsClient http, @NotNull PunishmentCache cache) {
        this.http = http;
        this.cache = cache;
    }

    @Override
    public @NotNull CompletableFuture<Optional<ActivePunishmentView>> findActive(@NotNull UUID playerUuid, @NotNull PunishmentKind kind) {
        Optional<CachedPunishment> cached = this.cache.peek(playerUuid, kind);

        if (cached.isPresent() && this.cache.isFresh(cached.get())) {
            return CompletableFuture.completedFuture(cached.get().asOptional());
        }

        return this.http.findActive(playerUuid, kind)
                .thenApply(result -> {
                    this.cache.store(playerUuid, kind, result);

                    return result;
                })
                .exceptionallyCompose(throwable -> cached
                        .filter(this.cache::isUsableStale)
                        .map(stale -> CompletableFuture.completedFuture(stale.asOptional()))
                        .orElseGet(() -> CompletableFuture.failedFuture(throwable)));
    }

    @Override
    public @NotNull CompletableFuture<ActivePunishmentView> issue(
            @NotNull UUID playerUuid, @NotNull PunishmentKind kind,
            @NotNull UUID issuedByUuid, @NotNull String issuedByName,
            @NotNull String reason, @Nullable Duration duration
    ) {
        return this.http.issue(playerUuid, kind, issuedByUuid, issuedByName, reason, duration)
                .thenApply(punishment -> {
                    this.cache.store(playerUuid, kind, Optional.of(punishment));

                    return punishment;
                });
    }

    @Override
    public @NotNull CompletableFuture<Void> lift(
            @NotNull UUID playerUuid, @NotNull PunishmentKind kind,
            @NotNull UUID liftedByUuid, @NotNull String liftedByName, @NotNull String reason
    ) {
        return this.http.lift(playerUuid, kind, liftedByUuid, liftedByName, reason)
                .thenRun(() -> this.cache.store(playerUuid, kind, Optional.empty()));
    }

    @Override
    public @NotNull CompletableFuture<PunishmentHistoryEntry> warn(
            @NotNull UUID playerUuid, @NotNull UUID issuedByUuid,
            @NotNull String issuedByName, @NotNull String reason
    ) {
        return this.http.warn(playerUuid, issuedByUuid, issuedByName, reason);
    }

    @Override
    public @NotNull CompletableFuture<List<ActivePunishmentView>> listActive(@NotNull PunishmentKind kind, int limit, int offset) {
        return this.http.listActive(kind, limit, offset);
    }

    @Override
    public @NotNull CompletableFuture<List<PunishmentHistoryEntry>> history(@NotNull UUID playerUuid, int limit, int offset) {
        return this.http.history(playerUuid, limit, offset);
    }

    @Override
    public @NotNull CompletableFuture<Optional<UUID>> resolveUuid(@NotNull String login) {
        return this.http.lookup(login);
    }

    @Override
    public void invalidate(@NotNull UUID playerUuid) {
        this.cache.invalidate(playerUuid);
    }

    @Override
    public void invalidate(@NotNull UUID playerUuid, @NotNull PunishmentKind kind) {
        this.cache.invalidate(playerUuid, kind);
    }

    public @NotNull CompletableFuture<Void> rememberPlayer(@NotNull UUID uuid, @NotNull String login) {
        return this.http.rememberPlayer(uuid, login);
    }

    @Override
    public void close() {
        this.cache.invalidateAll();
        this.http.close();
    }

}
