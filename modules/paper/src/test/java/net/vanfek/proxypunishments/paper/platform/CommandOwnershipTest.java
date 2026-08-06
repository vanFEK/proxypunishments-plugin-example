package net.vanfek.proxypunishments.paper.platform;

import io.papermc.paper.configuration.ServerConfiguration;
import net.vanfek.proxypunishments.core.config.CommandsMode;
import org.bukkit.Server;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandOwnershipTest {

    @Test
    @DisplayName("auto: a standalone server owns the commands")
    void autoRegistersOnAStandaloneServer() {
        assertThat(CommandOwnership.registersCommands(CommandsMode.AUTO, false)).isTrue();
    }

    @Test
    @DisplayName("auto: behind a proxy the proxy owns the commands and nothing is registered")
    void autoRegistersNothingBehindAProxy() {
        assertThat(CommandOwnership.registersCommands(CommandsMode.AUTO, true)).isFalse();
    }

    @Test
    @DisplayName("always: registers even behind a proxy")
    void alwaysOverridesTheDetection() {
        assertThat(CommandOwnership.registersCommands(CommandsMode.ALWAYS, true)).isTrue();
        assertThat(CommandOwnership.registersCommands(CommandsMode.ALWAYS, false)).isTrue();
    }

    @Test
    @DisplayName("never: registers nothing even on a standalone server")
    void neverOverridesTheDetection() {
        assertThat(CommandOwnership.registersCommands(CommandsMode.NEVER, false)).isFalse();
        assertThat(CommandOwnership.registersCommands(CommandsMode.NEVER, true)).isFalse();
    }

    @Test
    @DisplayName("detection follows the server's own proxy-forwarding flag")
    void readsProxyForwardingFromTheServer() {
        assertThat(CommandOwnership.behindProxy(serverWithProxy(true))).isTrue();
        assertThat(CommandOwnership.behindProxy(serverWithProxy(false))).isFalse();
    }

    private static Server serverWithProxy(boolean enabled) {
        ServerConfiguration configuration = mock(ServerConfiguration.class);
        Server server = mock(Server.class);

        when(configuration.isProxyEnabled()).thenReturn(enabled);
        when(server.getServerConfig()).thenReturn(configuration);

        return server;
    }

}
