package net.vanfek.proxypunishments.core;

import net.vanfek.proxypunishments.api.ActivePunishmentView;
import net.vanfek.proxypunishments.api.AltView;
import net.vanfek.proxypunishments.api.PunishmentHistoryEntry;
import net.vanfek.proxypunishments.api.PunishmentKind;
import net.vanfek.proxypunishments.api.PunishmentService;
import net.vanfek.proxypunishments.core.cache.CachedPunishment;
import net.vanfek.proxypunishments.core.cache.PunishmentCache;
import net.vanfek.proxypunishments.core.client.PunishmentsClient;
import net.vanfek.proxypunishments.core.metrics.PluginMetrics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PunishmentServiceImpl implements PunishmentService, AutoCloseable {

    private final PunishmentsClient http;
    private final PunishmentCache cache;
    private final PluginMetrics metrics;

    public PunishmentServiceImpl(
            @NotNull PunishmentsClient http, @NotNull PunishmentCache cache,
            @NotNull PluginMetrics metrics
    ) {
        this.http = http;
        this.cache = cache;
        this.metrics = metrics;
    }

    public @NotNull PluginMetrics metrics() {
        return this.metrics;
    }

    public long cachedEntries() {
        return this.cache.size();
    }

    @Override
    public @NotNull CompletableFuture<Optional<ActivePunishmentView>> findActive(@NotNull UUID playerUuid, @NotNull PunishmentKind kind) {
        return lookup(
                this.cache.peek(playerUuid, kind),
                () -> this.http.findActive(playerUuid, kind),
                result -> this.cache.store(playerUuid, kind, result)
        );
    }

    @Override
    public @NotNull CompletableFuture<Optional<ActivePunishmentView>> findActiveIpBan(@NotNull String ipAddress) {
        return lookup(
                this.cache.peekIpBan(ipAddress),
                () -> this.http.findActiveIpBan(ipAddress),
                result -> this.cache.storeIpBan(ipAddress, result)
        );
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
    public @NotNull CompletableFuture<ActivePunishmentView> issueIpBan(
            @NotNull String ipAddress, @NotNull UUID playerUuid,
            @NotNull UUID issuedByUuid, @NotNull String issuedByName,
            @NotNull String reason, @Nullable Duration duration
    ) {
        return this.http.issueIpBan(ipAddress, playerUuid, issuedByUuid, issuedByName, reason, duration)
                .thenApply(punishment -> {
                    this.cache.storeIpBan(ipAddress, Optional.of(punishment));

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
    public @NotNull CompletableFuture<Void> liftIpBan(
            @NotNull String ipAddress, @NotNull UUID liftedByUuid,
            @NotNull String liftedByName, @NotNull String reason
    ) {
        return this.http.liftIpBan(ipAddress, liftedByUuid, liftedByName, reason)
                .thenRun(() -> this.cache.storeIpBan(ipAddress, Optional.empty()));
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
    public @NotNull CompletableFuture<List<AltView>> alts(@NotNull UUID playerUuid, int limit) {
        return this.http.alts(playerUuid, limit);
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

    @Override
    public void invalidateIpBan(@NotNull String ipAddress) {
        this.cache.invalidateIpBan(ipAddress);
    }

    public @NotNull CompletableFuture<Void> rememberPlayer(
            @NotNull UUID uuid, @NotNull String login,
            @Nullable String ipAddress
    ) {
        return this.http.rememberPlayer(uuid, login, ipAddress);
    }

    @Override
    public void close() {
        this.cache.invalidateAll();
        this.http.close();
    }

    private CompletableFuture<Optional<ActivePunishmentView>> lookup(
            Optional<CachedPunishment> cached,
            Supplier<CompletableFuture<Optional<ActivePunishmentView>>> fetch,
            Consumer<Optional<ActivePunishmentView>> store
    ) {
        if (cached.isPresent() && this.cache.isFresh(cached.get())) {
            this.metrics.cacheHit();

            return CompletableFuture.completedFuture(cached.get().asOptional());
        }

        this.metrics.cacheMiss();

        return fetch.get()
                .thenApply(result -> {
                    store.accept(result);

                    return result;
                })
                .exceptionallyCompose(throwable -> cached
                        .filter(this.cache::isUsableStale)
                        .map(stale -> staleAnswer(stale))
                        .orElseGet(() -> CompletableFuture.failedFuture(throwable)));
    }

    private CompletableFuture<Optional<ActivePunishmentView>> staleAnswer(CachedPunishment stale) {
        this.metrics.staleAnswer();

        return CompletableFuture.completedFuture(stale.asOptional());
    }

}
