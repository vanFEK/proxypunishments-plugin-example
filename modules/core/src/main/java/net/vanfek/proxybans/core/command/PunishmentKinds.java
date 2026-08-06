package net.vanfek.proxybans.core.command;

import net.vanfek.proxybans.api.PunishmentKind;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class PunishmentKinds {

    private static final List<String> NAMES = List.of("ban", "mute");

    private PunishmentKinds() {
    }

    public static @NotNull List<String> names() {
        return NAMES;
    }

    public static @NotNull Optional<PunishmentKind> parse(@NotNull String token) {
        return switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "ban", "bans" -> Optional.of(PunishmentKind.BAN);
            case "mute", "mutes" -> Optional.of(PunishmentKind.MUTE);
            default -> Optional.empty();
        };
    }

}
