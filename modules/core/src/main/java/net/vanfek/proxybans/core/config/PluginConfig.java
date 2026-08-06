package net.vanfek.proxybans.core.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.time.Duration;
import java.util.List;

@ConfigSerializable
public class PluginConfig {

    public Service service = new Service();
    public Cache cache = new Cache();
    public Login login = new Login();
    public Chat chat = new Chat();
    public Commands commands = new Commands();

    @ConfigSerializable
    public static class Service {

        public String baseUrl = "http://proxybans:8080";
        public String token = "";
        public Duration connectTimeout = Duration.ofSeconds(2);
        public Duration requestTimeout = Duration.ofSeconds(3);

    }

    @ConfigSerializable
    public static class Cache {

        public Duration activeTtl = Duration.ofSeconds(30);
        public Duration staleTtl = Duration.ofMinutes(30);
        public long maxSize = 100_000L;

    }

    @ConfigSerializable
    public static class Login {

        // Admits the player when the service is down and nothing usable is cached: locking out the network is worse.
        public boolean failOpen = true;

        public boolean rememberPlayer = true;

    }

    @ConfigSerializable
    public static class Chat {

        public boolean enforceMute = true;

        // Lets the message through when the service is down and nothing usable is cached, mirroring login.fail-open.
        public boolean failOpen = true;

        public List<String> blockedCommands = List.of(
                "msg", "tell", "w",
                "whisper", "r", "reply",
                "me", "say", "pm"
        );

    }

    @ConfigSerializable
    public static class Commands {

        public boolean registerTopLevelAliases = true;

        public CommandsMode mode = CommandsMode.AUTO;

    }

}
