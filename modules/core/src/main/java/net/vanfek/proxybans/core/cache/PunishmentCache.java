package net.vanfek.proxybans.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.vanfek.proxybans.api.ActivePunishmentView;
import net.vanfek.proxybans.api.PunishmentKind;
import net.vanfek.proxybans.core.config.PluginConfig;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fresh entries are served directly; stale ones exist only to keep punishments enforced while the service is unreachable. */
public final class PunishmentCache {

    private static final Duration EVICTION_GRACE = Duration.ofMinutes(1);

    private final Cache<PunishmentKey, CachedPunishment> entries;
    private final Duration activeTtl;
    private final Duration staleTtl;
    private final Clock clock;

    public PunishmentCache(@NotNull PluginConfig.Cache config, @NotNull Clock clock) {
        this.activeTtl = config.activeTtl;
        this.staleTtl = config.staleTtl;
        this.clock = clock;
        this.entries = Caffeine.newBuilder()
                .expireAfterWrite(config.staleTtl.plus(EVICTION_GRACE))
                .maximumSize(config.maxSize)
                .build();
    }

    public @NotNull Optional<CachedPunishment> peek(@NotNull UUID uuid, @NotNull PunishmentKind kind) {
        return Optional.ofNullable(this.entries.getIfPresent(PunishmentKey.of(uuid, kind)));
    }

    public boolean isFresh(@NotNull CachedPunishment entry) {
        return age(entry).compareTo(this.activeTtl) <= 0;
    }

    public boolean isUsableStale(@NotNull CachedPunishment entry) {
        return age(entry).compareTo(this.staleTtl) <= 0;
    }

    public void store(@NotNull UUID uuid, @NotNull PunishmentKind kind, @NotNull Optional<ActivePunishmentView> punishment) {
        Instant now = this.clock.instant();

        CachedPunishment entry = punishment
                .map(view -> CachedPunishment.present(view, now))
                .orElseGet(() -> CachedPunishment.absent(now));

        this.entries.put(PunishmentKey.of(uuid, kind), entry);
    }

    public void invalidate(@NotNull UUID uuid) {
        List<PunishmentKey> keys = new ArrayList<>(PunishmentKind.values().length);

        for (PunishmentKind kind : PunishmentKind.values()) {
            keys.add(PunishmentKey.of(uuid, kind));
        }

        this.entries.invalidateAll(keys);
    }

    public void invalidate(@NotNull UUID uuid, @NotNull PunishmentKind kind) {
        this.entries.invalidate(PunishmentKey.of(uuid, kind));
    }

    public void invalidateAll() {
        this.entries.invalidateAll();
    }

    public long size() {
        return this.entries.estimatedSize();
    }

    private Duration age(CachedPunishment entry) {
        return Duration.between(entry.cachedAt(), this.clock.instant());
    }

}
