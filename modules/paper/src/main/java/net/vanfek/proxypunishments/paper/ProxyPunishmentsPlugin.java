package net.vanfek.proxypunishments.paper;

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
import net.vanfek.proxypunishments.paper.command.CommandRegistrar;
import net.vanfek.proxypunishments.paper.command.support.CommandContext;
import net.vanfek.proxypunishments.paper.listener.ChatListener;
import net.vanfek.proxypunishments.paper.listener.LoginListener;
import net.vanfek.proxypunishments.paper.platform.CommandOwnership;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Clock;

public final class ProxyPunishmentsPlugin extends JavaPlugin {

    private final Clock clock = Clock.systemUTC();

    private Logger logger;
    private PunishmentServiceImpl punishments;
    private RedisInvalidationSubscriber invalidations;

    @Override
    public void onEnable() {
        this.logger = getSLF4JLogger();

        PluginConfig config = loadConfigOrBuiltInDefaults();
        MessageBundle messages;

        try {
            messages = MessageBundle.load(getDataPath());
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

        registerListeners(config, messages);
        registerCommandsIfOwned(config, context);
        warnAboutMissingToken(config);

        this.logger.info("ProxyPunishments ready — service {}, fail-open {}", config.service.baseUrl, config.login.failOpen);
    }

    @Override
    public void onDisable() {
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

    private void registerListeners(PluginConfig config, MessageBundle messages) {
        LoginListener loginListener = new LoginListener(this.punishments, messages, config.login, this.clock, this.logger);

        getServer().getPluginManager().registerEvents(loginListener, this);

        if (!config.chat.enforceMute) {
            this.logger.info("chat.enforce-mute is off — mutes are recorded but nothing is blocked on this server");

            return;
        }

        ChatListener chatListener = new ChatListener(this.punishments, messages, config.chat, this.clock, this.logger);

        getServer().getPluginManager().registerEvents(chatListener, this);
    }

    private void registerCommandsIfOwned(PluginConfig config, CommandContext context) {
        boolean behindProxy = CommandOwnership.behindProxy(getServer());

        if (!CommandOwnership.registersCommands(config.commands.mode, behindProxy)) {
            this.logger.info(
                    "Punishment commands are not registered here — commands.mode is {} and proxy forwarding is {}; "
                            + "issue punishments on the proxy, this server only enforces them",
                    config.commands.mode, behindProxy ? "enabled" : "disabled"
            );

            return;
        }

        this.logger.info(
                "Registering punishment commands — commands.mode is {} and proxy forwarding is {}",
                config.commands.mode, behindProxy ? "enabled" : "disabled"
        );

        new CommandRegistrar(getServer(), context, config.commands.registerTopLevelAliases, this.logger)
                .register(getLifecycleManager());
    }

    private PluginConfig loadConfigOrBuiltInDefaults() {
        try {
            return new ConfigLoader(getDataPath()).load();
        } catch (IOException | RuntimeException ex) {
            this.logger.error("Could not load config.yml — falling back to built-in defaults, "
                    + "punishment checks will fail until this is fixed", ex);

            return new PluginConfig();
        }
    }

    private PermissionChecker resolvePermissionChecker() {
        NativePermissionChecker fallback = new NativePermissionChecker();

        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            this.logger.info("LuckPerms not detected — using native Bukkit permission checks");

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
