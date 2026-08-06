package net.vanfek.proxybans.velocity.command.support;

import net.vanfek.proxybans.core.PunishmentServiceImpl;
import net.vanfek.proxybans.core.i18n.MessageBundle;
import net.vanfek.proxybans.core.permission.PermissionChecker;
import org.slf4j.Logger;

import java.time.Clock;

public record CommandContext(
        PunishmentServiceImpl punishments, MessageBundle messages,
        PermissionChecker permissions, Clock clock,
        Logger logger
) {
}
