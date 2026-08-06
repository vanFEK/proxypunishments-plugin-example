package net.vanfek.proxypunishments.velocity.command.support;

import net.vanfek.proxypunishments.core.PunishmentServiceImpl;
import net.vanfek.proxypunishments.core.config.RuntimeConfiguration;
import net.vanfek.proxypunishments.core.i18n.MessageBundle;
import net.vanfek.proxypunishments.core.permission.PermissionChecker;
import net.vanfek.proxypunishments.core.reload.ConfigurationReloader;
import org.slf4j.Logger;

import java.time.Clock;

public record CommandContext(
        PunishmentServiceImpl punishments, RuntimeConfiguration runtime,
        ConfigurationReloader reloader, PermissionChecker permissions,
        Clock clock, Logger logger
) {

    public MessageBundle messages() {
        return this.runtime.messages();
    }

}
