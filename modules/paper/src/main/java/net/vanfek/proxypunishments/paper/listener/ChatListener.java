package net.vanfek.proxypunishments.paper.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.vanfek.proxypunishments.api.ActivePunishmentView;
import net.vanfek.proxypunishments.api.PunishmentKind;
import net.vanfek.proxypunishments.core.PunishmentServiceImpl;
import net.vanfek.proxypunishments.core.config.RuntimeConfiguration;
import net.vanfek.proxypunishments.core.duration.DurationFormatter;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;

public final class ChatListener implements Listener {

    private final PunishmentServiceImpl punishments;
    private final RuntimeConfiguration runtime;
    private final Clock clock;
    private final Logger logger;

    private volatile BlockedCommands blocked;

    public ChatListener(
            @NotNull PunishmentServiceImpl punishments, @NotNull RuntimeConfiguration runtime,
            @NotNull Clock clock, @NotNull Logger logger
    ) {
        this.punishments = punishments;
        this.runtime = runtime;
        this.clock = clock;
        this.logger = logger;
        this.blocked = normalise(runtime.config().chat.blockedCommands);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(@NotNull AsyncChatEvent event) {
        if (!enforcesMute()) {
            return;
        }

        if (isMuted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // Unlike chat, this one runs on the main thread; the lookup is bounded by service.request-timeout and usually a cache hit.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(@NotNull PlayerCommandPreprocessEvent event) {
        if (!enforcesMute() || !isBlockedCommand(event.getMessage())) {
            return;
        }

        if (isMuted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    public boolean enforcesMute() {
        return this.runtime.config().chat.enforceMute;
    }

    public boolean isMuted(@NotNull Player player) {
        Optional<ActivePunishmentView> active;

        try {
            active = this.punishments.findActive(player.getUniqueId(), PunishmentKind.MUTE).join();
        } catch (CompletionException ex) {
            return onLookupFailure(player, ex);
        }

        Optional<ActivePunishmentView> enforceable = active.filter(this::isStillActive);

        if (enforceable.isEmpty()) {
            return false;
        }

        player.sendMessage(renderMute(enforceable.get()));

        return true;
    }

    public boolean isBlockedCommand(@NotNull String message) {
        return blockedCommands().contains(label(message));
    }

    private Set<String> blockedCommands() {
        List<String> configured = this.runtime.config().chat.blockedCommands;
        BlockedCommands current = this.blocked;

        if (current.configured().equals(configured)) {
            return current.labels();
        }

        BlockedCommands refreshed = normalise(configured);
        this.blocked = refreshed;

        return refreshed.labels();
    }

    private boolean onLookupFailure(Player player, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        String username = player.getName();

        if (this.runtime.config().chat.failOpen) {
            this.logger.warn("Mute lookup failed for {}, allowing the message (fail-open): {}", username, cause.getMessage());

            return false;
        }

        this.logger.error("Mute lookup failed for {} and fail-open is disabled, blocking the message: {}", username, cause.getMessage());
        player.sendMessage(this.runtime.messages().render("chat.service-unavailable"));

        return true;
    }

    private boolean isStillActive(ActivePunishmentView mute) {
        return mute.isPermanent() || !mute.isExpired(this.clock.instant());
    }

    private Component renderMute(ActivePunishmentView mute) {
        String key = mute.isPermanent() ? "chat.muted-permanent" : "chat.muted-temporary";
        Instant now = this.clock.instant();

        Duration remaining = mute.expiry()
                .map(expiry -> Duration.between(now, expiry))
                .orElse(Duration.ZERO);

        return this.runtime.messages().render(
                key, Placeholders.of("reason", mute.reason()), Placeholders.of("admin", mute.issuedByName()),
                Placeholders.of("remaining", mute.isPermanent() ? "—" : DurationFormatter.human(remaining)),
                Placeholders.of("expires_at", mute.expiry().map(Instant::toString).orElse("—"))
        );
    }

    private static BlockedCommands normalise(List<String> configured) {
        Set<String> labels = new HashSet<>();

        for (String command : configured) {
            labels.add(label(command));
        }

        return new BlockedCommands(List.copyOf(configured), Set.copyOf(labels));
    }

    private static String label(String message) {
        String trimmed = message.strip();
        String withoutSlash = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;

        int space = withoutSlash.indexOf(' ');
        String head = space < 0 ? withoutSlash : withoutSlash.substring(0, space);

        return head.substring(head.indexOf(':') + 1).toLowerCase(Locale.ROOT);
    }

    private static Throwable unwrap(Throwable throwable) {
        return throwable instanceof CompletionException completion && completion.getCause() != null
                ? completion.getCause()
                : throwable;
    }

    private record BlockedCommands(List<String> configured, Set<String> labels) {
    }

}
