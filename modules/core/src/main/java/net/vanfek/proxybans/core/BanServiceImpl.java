package net.vanfek.proxybans.core;

import net.vanfek.proxybans.api.ActiveBanView;
import net.vanfek.proxybans.api.BanService;
import net.vanfek.proxybans.api.PunishmentHistoryEntry;
import net.vanfek.proxybans.core.cache.BanCache;
import net.vanfek.proxybans.core.cache.CachedBan;
import net.vanfek.proxybans.core.client.BansClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BanServiceImpl implements BanService, AutoCloseable {

    private final BansClient http;
    private final BanCache cache;

    public BanServiceImpl(@NotNull BansClient http, @NotNull BanCache cache) {
        this.http = http;
        this.cache = cache;
    }

    @Override
    public @NotNull CompletableFuture<Optional<ActiveBanView>> findActiveBan(@NotNull UUID playerUuid) {
        Optional<CachedBan> cached = this.cache.peek(playerUuid);

        if (cached.isPresent() && this.cache.isFresh(cached.get())) {
            return CompletableFuture.completedFuture(cached.get().asOptional());
        }

        return this.http.findActiveBan(playerUuid)
                .thenApply(result -> {
                    this.cache.store(playerUuid, result);

                    return result;
                })
                .exceptionallyCompose(throwable -> cached
                        .filter(this.cache::isUsableStale)
                        .map(stale -> CompletableFuture.completedFuture(stale.asOptional()))
                        .orElseGet(() -> CompletableFuture.failedFuture(throwable)));
    }

    @Override
    public @NotNull CompletableFuture<ActiveBanView> issueBan(
            @NotNull UUID playerUuid,
            @NotNull UUID issuedByUuid, @NotNull String issuedByName,
            @NotNull String reason, @Nullable Duration duration
    ) {
        return this.http.issueBan(playerUuid, issuedByUuid, issuedByName, reason, duration)
                .thenApply(ban -> {
                    this.cache.store(playerUuid, Optional.of(ban));

                    return ban;
                });
    }

    @Override
    public @NotNull CompletableFuture<Void> liftBan(
            @NotNull UUID playerUuid,
            @NotNull UUID liftedByUuid, @NotNull String liftedByName,
            @NotNull String reason
    ) {
        return this.http.liftBan(playerUuid, liftedByUuid, liftedByName, reason)
                .thenRun(() -> this.cache.store(playerUuid, Optional.empty()));
    }

    @Override
    public @NotNull CompletableFuture<List<PunishmentHistoryEntry>> history(@NotNull UUID playerUuid, int limit, int offset) {
        return this.http.history(playerUuid, limit, offset);
    }

    @Override
    public @NotNull CompletableFuture<List<ActiveBanView>> listActive(int limit, int offset) {
        return this.http.listActiveBans(limit, offset);
    }

    @Override
    public @NotNull CompletableFuture<Optional<UUID>> resolveUuid(@NotNull String login) {
        return this.http.lookup(login);
    }

    @Override
    public void invalidate(@NotNull UUID playerUuid) {
        this.cache.invalidate(playerUuid);
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
