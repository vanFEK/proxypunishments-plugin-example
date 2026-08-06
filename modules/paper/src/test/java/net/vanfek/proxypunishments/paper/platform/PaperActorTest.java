package net.vanfek.proxypunishments.paper.platform;

import net.vanfek.proxypunishments.api.platform.Actor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperActorTest {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    @DisplayName("the console has no player UUID, so it reports the reserved console one")
    void mapsTheConsoleToTheReservedUuid() {
        PaperActor actor = PaperActor.of(mock(ConsoleCommandSender.class));

        assertThat(actor.uuid()).isEqualTo(Actor.CONSOLE_UUID);
        assertThat(actor.name()).isEqualTo("Console");
        assertThat(actor.isPlayer()).isFalse();
    }

    @Test
    void keepsThePlayersOwnIdentity() {
        Player player = mock(Player.class);

        when(player.getUniqueId()).thenReturn(PLAYER_UUID);
        when(player.getName()).thenReturn("Steve");

        CommandSender sender = player;
        PaperActor actor = PaperActor.of(sender);

        assertThat(actor.uuid()).isEqualTo(PLAYER_UUID);
        assertThat(actor.name()).isEqualTo("Steve");
        assertThat(actor.isPlayer()).isTrue();
    }

    @Test
    @DisplayName("permission checks always go to the sender, console included")
    void delegatesPermissionChecksToTheSender() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);

        when(console.hasPermission("proxypunishments.command.ban")).thenReturn(true);

        assertThat(PaperActor.of(console).hasPermission("proxypunishments.command.ban")).isTrue();
        assertThat(PaperActor.of(console).hasPermission("proxypunishments.command.mute")).isFalse();
    }

}
