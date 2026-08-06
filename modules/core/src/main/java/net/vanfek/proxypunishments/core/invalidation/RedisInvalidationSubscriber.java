package net.vanfek.proxypunishments.core.invalidation;

import net.vanfek.proxypunishments.api.PunishmentService;
import net.vanfek.proxypunishments.core.config.PluginConfig;
import net.vanfek.proxypunishments.core.metrics.PluginMetrics;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import redis.clients.jedis.Connection;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPubSub;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Its own daemon thread: the SUBSCRIBE read blocks forever by design and must never sit on a Netty or server thread. */
public final class RedisInvalidationSubscriber implements AutoCloseable {

    private static final String THREAD_NAME = "proxypunishments-redis-subscriber";
    private static final String CLIENT_NAME = "proxypunishments";
    private static final int NO_SOCKET_TIMEOUT = 0;

    private final PluginConfig.Redis config;
    private final PunishmentService punishments;
    private final PluginMetrics metrics;
    private final Logger logger;

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean failureLogged = new AtomicBoolean();
    private final AtomicReference<Connection> connection = new AtomicReference<>();

    private final JedisPubSub listener = new JedisPubSub() {

        @Override
        public void onMessage(String channel, String message) {
            accept(message);
        }

    };

    private Thread worker;

    public RedisInvalidationSubscriber(
            @NotNull PluginConfig.Redis config, @NotNull PunishmentService punishments,
            @NotNull PluginMetrics metrics, @NotNull Logger logger
    ) {
        this.config = config;
        this.punishments = punishments;
        this.metrics = metrics;
        this.logger = logger;
    }

    public void start() {
        if (!this.config.enabled) {
            this.logger.info("Redis cache invalidation is off — cached answers expire after cache.active-ttl as before");

            return;
        }

        if (!this.running.compareAndSet(false, true)) {
            return;
        }

        this.metrics.invalidationsEnabled(true);
        this.worker = Thread.ofPlatform().daemon().name(THREAD_NAME).start(this::run);
    }

    public void accept(@NotNull String payload) {
        InvalidationCodec.decode(payload).ifPresent(this::apply);
    }

    @Override
    public void close() {
        if (!this.running.compareAndSet(true, false)) {
            return;
        }

        Connection open = this.connection.getAndSet(null);

        if (open != null) {
            closeQuietly(open);
        }

        Thread current = this.worker;

        if (current != null) {
            current.interrupt();
        }
    }

    private void apply(InvalidationMessage message) {
        if (message.targetsAddress()) {
            this.punishments.invalidateIpBan(message.target());
            this.metrics.invalidationReceived();

            return;
        }

        Optional<UUID> playerUuid = message.playerUuid();

        if (playerUuid.isEmpty()) {
            return;
        }

        this.punishments.invalidate(playerUuid.get(), message.kind());
        this.metrics.invalidationReceived();
    }

    private void run() {
        while (this.running.get()) {
            subscribeOnce();

            if (!this.running.get()) {
                return;
            }

            if (!waitBeforeReconnect()) {
                return;
            }
        }
    }

    private void subscribeOnce() {
        try (Connection open = openConnection()) {
            this.connection.set(open);
            this.metrics.invalidationsSubscribed(true);
            this.failureLogged.set(false);

            this.logger.info("Subscribed to Redis channel {} for punishment cache invalidation", this.config.channel);

            this.listener.proceed(open, this.config.channel);
        } catch (RuntimeException ex) {
            reportFailure(ex);
        } finally {
            this.connection.set(null);
            this.metrics.invalidationsSubscribed(false);
        }
    }

    private Connection openConnection() {
        DefaultJedisClientConfig.Builder client = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis((int) this.config.connectTimeout.toMillis())
                .socketTimeoutMillis(NO_SOCKET_TIMEOUT)
                .database(this.config.database)
                .clientName(CLIENT_NAME);

        if (!this.config.username.isBlank()) {
            client.user(this.config.username);
        }

        if (!this.config.password.isBlank()) {
            client.password(this.config.password);
        }

        return new Connection(new HostAndPort(this.config.host, this.config.port), client.build());
    }

    private boolean waitBeforeReconnect() {
        try {
            Thread.sleep(this.config.reconnectDelay.toMillis());

            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            return false;
        }
    }

    private void reportFailure(RuntimeException ex) {
        if (!this.running.get()) {
            return;
        }

        if (this.failureLogged.compareAndSet(false, true)) {
            this.logger.warn("Redis invalidation subscription failed — punishments still work, cached answers just "
                    + "expire after cache.active-ttl; reconnecting in the background", ex);
        }
    }

    private void closeQuietly(Connection open) {
        try {
            open.close();
        } catch (RuntimeException ex) {
            this.logger.debug("Redis connection could not be closed cleanly", ex);
        }
    }

}
