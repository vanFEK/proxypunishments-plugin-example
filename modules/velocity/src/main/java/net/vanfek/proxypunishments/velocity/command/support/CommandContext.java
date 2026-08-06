package net.vanfek.proxypunishments.velocity.command.support;

import net.vanfek.proxypunishments.core.PunishmentServiceImpl;
import net.vanfek.proxypunishments.core.i18n.MessageBundle;
import net.vanfek.proxypunishments.core.permission.PermissionChecker;
import org.slf4j.Logger;

import java.time.Clock;

public record CommandContext(
        PunishmentServiceImpl punishments, MessageBundle messages,
        PermissionChecker permissions, Clock clock,
        Logger logger
) {
}
