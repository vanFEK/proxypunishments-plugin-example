package net.vanfek.proxybans.velocity.command;

import net.vanfek.proxybans.core.BanServiceImpl;
import net.vanfek.proxybans.core.i18n.MessageBundle;
import net.vanfek.proxybans.core.permission.PermissionChecker;
import org.slf4j.Logger;

import java.time.Clock;

/**
 * Everything a command needs, passed as one value so command builders stay static and testable.
 */
public record CommandContext(
        BanServiceImpl bans,
        MessageBundle messages,
        PermissionChecker permissions,
        Clock clock,
        Logger logger
) {
}
