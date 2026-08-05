package net.vanfek.proxybans.core.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.time.Duration;

@ConfigSerializable
public class PluginConfig {

    public Service service = new Service();
    public Cache cache = new Cache();
    public Login login = new Login();
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

        /**
         * When the service is unreachable and no usable cached answer exists, let the player in
         * anyway. Keeping a whole network locked out because one internal service restarted is
         * usually worse than briefly letting one banned player through.
         */
        public boolean failOpen = true;

        /**
         * Report the UUID↔name pair to the service on login. Only meaningful in {@code local} mode,
         * where the service has no other way of learning who a player is.
         */
        public boolean rememberPlayer = true;

    }

    @ConfigSerializable
    public static class Commands {

        public boolean registerTopLevelAliases = true;

    }

}
