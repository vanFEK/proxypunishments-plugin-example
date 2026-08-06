package net.vanfek.proxybans.velocity;

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
import net.vanfek.proxybans.core.BanServiceImpl;
import net.vanfek.proxybans.core.cache.BanCache;
import net.vanfek.proxybans.core.client.BansHttpClient;
import net.vanfek.proxybans.core.config.ConfigLoader;
import net.vanfek.proxybans.core.config.PluginConfig;
import net.vanfek.proxybans.core.i18n.MessageBundle;
import net.vanfek.proxybans.core.permission.LuckPermsPermissionChecker;
import net.vanfek.proxybans.core.permission.NativePermissionChecker;
import net.vanfek.proxybans.core.permission.PermissionChecker;
import net.vanfek.proxybans.velocity.command.CommandRegistrar;
import net.vanfek.proxybans.velocity.command.support.CommandContext;
import net.vanfek.proxybans.velocity.listener.LoginListener;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;

@Plugin(
        id = "proxybans",
        name = "ProxyBans",
        version = "1.0.0",
        description = "Proxy-side ban system for Velocity, backed by a standalone bans service",
        url = "https://github.com/vanfek/proxybans-plugin",
        authors = {"vanfek"},
        dependencies = {@Dependency(id = "luckperms", optional = true)}
)
public final class ProxyBansPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final Clock clock;

    private BanServiceImpl banService;

    @Inject
    public ProxyBansPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
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
            this.logger.error("Could not load messages.yml — ProxyBans will not start", ex);

            return;
        }

        BansHttpClient httpClient = new BansHttpClient(config.service);
        BanCache cache = new BanCache(config.cache, this.clock);

        this.banService = new BanServiceImpl(httpClient, cache);

        PermissionChecker permissions = resolvePermissionChecker();

        CommandContext context = new CommandContext(this.banService, messages, permissions, this.clock, this.logger);
        LoginListener loginListener = new LoginListener(this.banService, messages, config.login, this.clock, this.logger);

        this.proxy.getEventManager().register(this, loginListener);

        new CommandRegistrar(this.proxy, context, config.commands.registerTopLevelAliases, this.logger).register(this);

        warnAboutMissingToken(config);

        this.logger.info("ProxyBans ready — service {}, fail-open {}", config.service.baseUrl, config.login.failOpen);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (this.banService == null) {
            return;
        }

        this.banService.close();
    }

    private PluginConfig loadConfigOrBuiltInDefaults() {
        try {
            return new ConfigLoader(this.dataDirectory).load();
        } catch (IOException | RuntimeException ex) {
            this.logger.error("Could not load config.yml — falling back to built-in defaults, "
                    + "ban checks will fail until this is fixed", ex);

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

        this.logger.error("service.token is empty in config.yml — the bans service rejects unauthenticated "
                + "requests, so every ban check will fail until a token is set");
    }

}
