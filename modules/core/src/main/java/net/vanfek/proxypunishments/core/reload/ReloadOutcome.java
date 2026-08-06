package net.vanfek.proxypunishments.core.reload;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ReloadOutcome(boolean applied, String failure, List<BoundSetting> needingRestart) {

    public static @NotNull ReloadOutcome swapped(@NotNull List<BoundSetting> needingRestart) {
        return new ReloadOutcome(true, "", needingRestart);
    }

    public static @NotNull ReloadOutcome rejected(@NotNull String failure) {
        return new ReloadOutcome(false, failure, List.of());
    }

}
