package net.vanfek.proxybans.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.vanfek.proxybans.api.ActiveBanView;
import net.vanfek.proxybans.core.config.PluginConfig;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Fresh entries are served directly; stale ones exist only to keep bans enforced while the service is unreachable. */
public final class BanCache {

    private static final Duration EVICTION_GRACE = Duration.ofMinutes(1);

    private final Cache<UUID, CachedBan> entries;
    private final Duration activeTtl;
    private final Duration staleTtl;
    private final Clock clock;

    public BanCache(@NotNull PluginConfig.Cache config, @NotNull Clock clock) {
        this.activeTtl = config.activeTtl;
        this.staleTtl = config.staleTtl;
        this.clock = clock;
        this.entries = Caffeine.newBuilder()
                .expireAfterWrite(config.staleTtl.plus(EVICTION_GRACE))
                .maximumSize(config.maxSize)
                .build();
    }

    public @NotNull Optional<CachedBan> peek(@NotNull UUID uuid) {
        return Optional.ofNullable(this.entries.getIfPresent(uuid));
    }

    public boolean isFresh(@NotNull CachedBan entry) {
        return age(entry).compareTo(this.activeTtl) <= 0;
    }

    public boolean isUsableStale(@NotNull CachedBan entry) {
        return age(entry).compareTo(this.staleTtl) <= 0;
    }

    public void store(@NotNull UUID uuid, @NotNull Optional<ActiveBanView> ban) {
        Instant now = this.clock.instant();

        CachedBan entry = ban
                .map(view -> CachedBan.present(view, now))
                .orElseGet(() -> CachedBan.absent(now));

        this.entries.put(uuid, entry);
    }

    public void invalidate(@NotNull UUID uuid) {
        this.entries.invalidate(uuid);
    }

    public void invalidateAll() {
        this.entries.invalidateAll();
    }

    public long size() {
        return this.entries.estimatedSize();
    }

    private Duration age(CachedBan entry) {
        return Duration.between(entry.cachedAt(), this.clock.instant());
    }

}
