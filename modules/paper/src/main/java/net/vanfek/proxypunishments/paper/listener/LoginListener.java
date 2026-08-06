package net.vanfek.proxypunishments.paper.listener;

import net.kyori.adventure.text.Component;
import net.vanfek.proxypunishments.api.ActivePunishmentView;
import net.vanfek.proxypunishments.api.PunishmentKind;
import net.vanfek.proxypunishments.core.PunishmentServiceImpl;
import net.vanfek.proxypunishments.core.config.PluginConfig;
import net.vanfek.proxypunishments.core.duration.DurationFormatter;
import net.vanfek.proxypunishments.core.i18n.MessageBundle;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

// Fail-open: one banned player slipping in beats locking the server out; the stale cache covers brief outages.
public final class LoginListener implements Listener {

    private final PunishmentServiceImpl punishments;
    private final MessageBundle messages;
    private final PluginConfig.Login config;
    private final Clock clock;
    private final Logger logger;

    public LoginListener(
            @NotNull PunishmentServiceImpl punishments, @NotNull MessageBundle messages,
            @NotNull PluginConfig.Login config, @NotNull Clock clock,
            @NotNull Logger logger
    ) {
        this.punishments = punishments;
        this.messages = messages;
        this.config = config;
        this.clock = clock;
        this.logger = logger;
    }

    // AsyncPlayerPreLoginEvent already runs off the main thread, so waiting for the lookup here blocks nothing but the joiner.
    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        handle(event).join();
    }

    public @NotNull CompletableFuture<Void> handle(@NotNull AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();

        return this.punishments.findActive(uuid, PunishmentKind.BAN)
                .thenCompose(active -> {
                    Optional<ActivePunishmentView> enforceable = active.filter(this::isStillActive);

                    if (enforceable.isPresent()) {
                        event.disallow(Result.KICK_BANNED, renderBan(enforceable.get()));

                        return CompletableFuture.completedFuture(null);
                    }

                    return rememberIfNeeded(event);
                })
                .exceptionally(throwable -> {
                    onLookupFailure(event, throwable);

                    return null;
                });
    }

    private CompletableFuture<Void> rememberIfNeeded(AsyncPlayerPreLoginEvent event) {
        if (!this.config.rememberPlayer) {
            return CompletableFuture.completedFuture(null);
        }

        String username = event.getName();

        return this.punishments.rememberPlayer(event.getUniqueId(), username)
                .exceptionally(throwable -> {
                    this.logger.warn("Failed to remember player {}: {}", username, unwrap(throwable).getMessage());

                    return null;
                });
    }

    private void onLookupFailure(AsyncPlayerPreLoginEvent event, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        String username = event.getName();

        if (this.config.failOpen) {
            this.logger.warn("Ban lookup failed for {}, allowing login (fail-open): {}", username, cause.getMessage());

            return;
        }

        this.logger.error("Ban lookup failed for {} and fail-open is disabled, denying login: {}", username, cause.getMessage());

        event.disallow(Result.KICK_OTHER, this.messages.render("login.service-unavailable"));
    }

    private boolean isStillActive(ActivePunishmentView ban) {
        return ban.isPermanent() || !ban.isExpired(this.clock.instant());
    }

    private Component renderBan(ActivePunishmentView ban) {
        String key = ban.isPermanent() ? "login.kick-permanent" : "login.kick-temporary";
        Instant now = this.clock.instant();

        Duration remaining = ban.expiry()
                .map(expiry -> Duration.between(now, expiry))
                .orElse(Duration.ZERO);

        return this.messages.render(
                key, Placeholders.of("reason", ban.reason()), Placeholders.of("admin", ban.issuedByName()),
                Placeholders.of("remaining", ban.isPermanent() ? "—" : DurationFormatter.human(remaining)),
                Placeholders.of("expires_at", ban.expiry().map(Instant::toString).orElse("—"))
        );
    }

    private static Throwable unwrap(Throwable throwable) {
        return throwable instanceof CompletionException completion && completion.getCause() != null
                ? completion.getCause()
                : throwable;
    }

}
