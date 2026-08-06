package net.vanfek.proxypunishments.core.reload;

import net.vanfek.proxypunishments.core.config.PluginConfig;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Settings the plugin binds while starting: a reload reads the new value but nothing acts on it until a restart. */
public final class BoundSettings {

    private static final String SECRET = "***";

    private BoundSettings() {
    }

    public static @NotNull List<BoundSetting> changed(@NotNull PluginConfig running, @NotNull PluginConfig configured) {
        List<BoundSetting> changed = new ArrayList<>();

        addService(changed, running.service, configured.service);
        addCache(changed, running.cache, configured.cache);
        addRedis(changed, running.redis, configured.redis);
        addCommands(changed, running.commands, configured.commands);

        return List.copyOf(changed);
    }

    private static void addService(List<BoundSetting> changed, PluginConfig.Service running, PluginConfig.Service configured) {
        add(changed, "service.base-url", running.baseUrl, configured.baseUrl);
        addSecret(changed, "service.token", running.token, configured.token);
        add(changed, "service.connect-timeout", running.connectTimeout, configured.connectTimeout);
        add(changed, "service.request-timeout", running.requestTimeout, configured.requestTimeout);
    }

    private static void addCache(List<BoundSetting> changed, PluginConfig.Cache running, PluginConfig.Cache configured) {
        add(changed, "cache.active-ttl", running.activeTtl, configured.activeTtl);
        add(changed, "cache.stale-ttl", running.staleTtl, configured.staleTtl);
        add(changed, "cache.max-size", running.maxSize, configured.maxSize);
    }

    private static void addRedis(List<BoundSetting> changed, PluginConfig.Redis running, PluginConfig.Redis configured) {
        add(changed, "redis.enabled", running.enabled, configured.enabled);
        add(changed, "redis.host", running.host, configured.host);
        add(changed, "redis.port", running.port, configured.port);
        add(changed, "redis.username", running.username, configured.username);
        addSecret(changed, "redis.password", running.password, configured.password);
        add(changed, "redis.database", running.database, configured.database);
        add(changed, "redis.channel", running.channel, configured.channel);
        add(changed, "redis.connect-timeout", running.connectTimeout, configured.connectTimeout);
        add(changed, "redis.reconnect-delay", running.reconnectDelay, configured.reconnectDelay);
    }

    private static void addCommands(List<BoundSetting> changed, PluginConfig.Commands running, PluginConfig.Commands configured) {
        add(changed, "commands.register-top-level-aliases", running.registerTopLevelAliases, configured.registerTopLevelAliases);
        add(changed, "commands.mode", running.mode, configured.mode);
    }

    private static void add(List<BoundSetting> changed, String path, Object running, Object configured) {
        if (Objects.equals(running, configured)) {
            return;
        }

        changed.add(new BoundSetting(path, String.valueOf(running), String.valueOf(configured)));
    }

    private static void addSecret(List<BoundSetting> changed, String path, Object running, Object configured) {
        if (Objects.equals(running, configured)) {
            return;
        }

        changed.add(new BoundSetting(path, SECRET, SECRET));
    }

}
