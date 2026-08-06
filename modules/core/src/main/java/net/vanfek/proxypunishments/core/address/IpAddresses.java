package net.vanfek.proxypunishments.core.address;

import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

public final class IpAddresses {

    private static final int IPV4_GROUPS = 4;
    private static final int IPV4_GROUP_DIGITS = 3;
    private static final int IPV4_GROUP_MAX = 255;

    private IpAddresses() {
    }

    public static @NotNull Optional<String> parse(@NotNull String token) {
        String trimmed = token.trim();

        if (trimmed.isEmpty() || trimmed.indexOf('%') >= 0) {
            return Optional.empty();
        }

        if (isIpv4(trimmed)) {
            return Optional.of(trimmed);
        }

        if (trimmed.indexOf(':') < 0) {
            return Optional.empty();
        }

        return parseIpv6(trimmed);
    }

    // A colon never appears in a host name, so this branch can never turn into a DNS lookup.
    private static Optional<String> parseIpv6(String value) {
        try {
            return Optional.of(InetAddress.getByName(value).getHostAddress());
        } catch (UnknownHostException ex) {
            return Optional.empty();
        }
    }

    private static boolean isIpv4(String value) {
        String[] groups = value.split("\\.", -1);

        if (groups.length != IPV4_GROUPS) {
            return false;
        }

        for (String group : groups) {
            if (!isGroup(group)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isGroup(String group) {
        if (group.isEmpty() || group.length() > IPV4_GROUP_DIGITS) {
            return false;
        }

        for (int index = 0; index < group.length(); index++) {
            char digit = group.charAt(index);

            if (digit < '0' || digit > '9') {
                return false;
            }
        }

        return Integer.parseInt(group) <= IPV4_GROUP_MAX;
    }

}
