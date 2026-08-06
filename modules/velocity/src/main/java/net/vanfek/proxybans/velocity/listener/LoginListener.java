package net.vanfek.proxybans.velocity.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent.ComponentResult;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.vanfek.proxybans.api.ActivePunishmentView;
import net.vanfek.proxybans.api.PunishmentKind;
import net.vanfek.proxybans.core.PunishmentServiceImpl;
import net.vanfek.proxybans.core.config.PluginConfig;
import net.vanfek.proxybans.core.duration.DurationFormatter;
import net.vanfek.proxybans.core.i18n.MessageBundle;
import net.vanfek.proxybans.core.i18n.Placeholders;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

// Fail-open: one banned player slipping in beats locking the network out; the ban service's stale cache covers brief outages.
public final class LoginListener {

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

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        return EventTask.resumeWhenComplete(handle(event));
    }

    public @NotNull CompletableFuture<Void> handle(@NotNull LoginEvent event) {
        Player player = event.getPlayer();

        return this.punishments.findActive(player.getUniqueId(), PunishmentKind.BAN)
                .thenCompose(active -> {
                    Optional<ActivePunishmentView> enforceable = active.filter(this::isStillActive);

                    if (enforceable.isPresent()) {
                        event.setResult(ComponentResult.denied(renderBan(enforceable.get())));

                        return CompletableFuture.completedFuture(null);
                    }

                    return rememberIfNeeded(player);
                })
                .exceptionally(throwable -> {
                    onLookupFailure(event, throwable);

                    return null;
                });
    }

    private CompletableFuture<Void> rememberIfNeeded(Player player) {
        if (!this.config.rememberPlayer) {
            return CompletableFuture.completedFuture(null);
        }

        return this.punishments.rememberPlayer(player.getUniqueId(), player.getUsername())
                .exceptionally(throwable -> {
                    this.logger.warn("Failed to remember player {}: {}", player.getUsername(), unwrap(throwable).getMessage());

                    return null;
                });
    }

    private void onLookupFailure(LoginEvent event, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        String username = event.getPlayer().getUsername();

        if (this.config.failOpen) {
            this.logger.warn("Ban lookup failed for {}, allowing login (fail-open): {}", username, cause.getMessage());

            return;
        }

        this.logger.error("Ban lookup failed for {} and fail-open is disabled, denying login: {}", username, cause.getMessage());

        event.setResult(ComponentResult.denied(this.messages.render("login.service-unavailable")));
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
