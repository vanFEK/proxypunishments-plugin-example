package net.vanfek.proxypunishments.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.vanfek.proxypunishments.core.PunishmentServiceImpl;
import net.vanfek.proxypunishments.core.cache.PunishmentCache;
import net.vanfek.proxypunishments.core.client.PunishmentsHttpClient;
import net.vanfek.proxypunishments.core.config.ConfigLoader;
import net.vanfek.proxypunishments.core.config.PluginConfig;
import net.vanfek.proxypunishments.core.i18n.MessageBundle;
import net.vanfek.proxypunishments.core.invalidation.RedisInvalidationSubscriber;
import net.vanfek.proxypunishments.core.metrics.PluginMetrics;
import net.vanfek.proxypunishments.core.metrics.StatusFormat;
import net.vanfek.proxypunishments.core.permission.LuckPermsPermissionChecker;
import net.vanfek.proxypunishments.core.permission.NativePermissionChecker;
import net.vanfek.proxypunishments.core.permission.PermissionChecker;
import net.vanfek.proxypunishments.velocity.command.CommandRegistrar;
import net.vanfek.proxypunishments.velocity.command.support.CommandContext;
import net.vanfek.proxypunishments.velocity.listener.LoginListener;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;

@Plugin(
        id = "proxypunishments",
        name = "ProxyPunishments",
        version = "1.0.0",
        description = "Proxy-side punishment system for Velocity, backed by a standalone punishments service",
        url = "https://github.com/vanfek/proxypunishments-plugin",
        authors = {"vanfek"},
        dependencies = {@Dependency(id = "luckperms", optional = true)}
)
public final class ProxyPunishmentsPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final Clock clock;

    private PunishmentServiceImpl punishments;
    private RedisInvalidationSubscriber invalidations;

    @Inject
    public ProxyPunishmentsPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.clock = Clock.systemUTC();
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        PluginConfig config = loadConfigOrBuiltInDefaults();
        MessageBundle messages;

        try {
            messages = MessageBundle.load(this.dataDirectory);
        } catch (IOException ex) {
            this.logger.error("Could not load messages.yml — ProxyPunishments will not start", ex);

            return;
        }

        PluginMetrics metrics = new PluginMetrics();
        PunishmentsHttpClient httpClient = new PunishmentsHttpClient(config.service, metrics);
        PunishmentCache cache = new PunishmentCache(config.cache, this.clock);

        this.punishments = new PunishmentServiceImpl(httpClient, cache, metrics);
        this.invalidations = new RedisInvalidationSubscriber(config.redis, this.punishments, metrics, this.logger);

        this.invalidations.start();

        PermissionChecker permissions = resolvePermissionChecker();

        CommandContext context = new CommandContext(this.punishments, messages, permissions, this.clock, this.logger);
        LoginListener loginListener = new LoginListener(this.punishments, messages, config.login, this.clock, this.logger);

        this.proxy.getEventManager().register(this, loginListener);

        new CommandRegistrar(this.proxy, context, config.commands.registerTopLevelAliases, this.logger).register(this);

        warnAboutMissingToken(config);

        this.logger.info("ProxyPunishments ready — service {}, fail-open {}", config.service.baseUrl, config.login.failOpen);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (this.invalidations != null) {
            this.invalidations.close();
        }

        if (this.punishments == null) {
            return;
        }

        this.logger.info("ProxyPunishments stopping — {}", StatusFormat.summary(
                this.punishments.metrics().snapshot(), this.punishments.cachedEntries()
        ));

        this.punishments.close();
    }

    private PluginConfig loadConfigOrBuiltInDefaults() {
        try {
            return new ConfigLoader(this.dataDirectory).load();
        } catch (IOException | RuntimeException ex) {
            this.logger.error("Could not load config.yml — falling back to built-in defaults, "
                    + "punishment checks will fail until this is fixed", ex);

            return new PluginConfig();
        }
    }

    private PermissionChecker resolvePermissionChecker() {
        NativePermissionChecker fallback = new NativePermissionChecker();

        if (this.proxy.getPluginManager().getPlugin("luckperms").isEmpty()) {
            this.logger.info("LuckPerms not detected — using native Velocity permission checks");

            return fallback;
        }

        try {
            LuckPerms luckPerms = LuckPermsProvider.get();

            this.logger.info("LuckPerms detected — delegating permission checks");

            return new LuckPermsPermissionChecker(luckPerms, fallback);
        } catch (IllegalStateException ex) {
            this.logger.warn("LuckPerms is present but its API is not initialised yet — falling back to native checks");

            return fallback;
        }
    }

    private void warnAboutMissingToken(PluginConfig config) {
        if (!config.service.token.isBlank()) {
            return;
        }

        this.logger.error("service.token is empty in config.yml — the punishments service rejects unauthenticated "
                + "requests, so every punishment check will fail until a token is set");
    }

}
