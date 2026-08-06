package net.vanfek.proxypunishments.core.address;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IpAddressesTest {

    @Test
    void acceptsIpv4Literals() {
        assertThat(IpAddresses.parse("203.0.113.7")).contains("203.0.113.7");
        assertThat(IpAddresses.parse("  198.51.100.9  ")).contains("198.51.100.9");
        assertThat(IpAddresses.parse("0.0.0.0")).contains("0.0.0.0");
        assertThat(IpAddresses.parse("255.255.255.255")).contains("255.255.255.255");
    }

    @Test
    void acceptsIpv6Literals() {
        assertThat(IpAddresses.parse("2001:db8::1")).isPresent();
        assertThat(IpAddresses.parse("::1")).isPresent();
    }

    @Test
    @DisplayName("garbage is rejected rather than turned into an address")
    void rejectsGarbage() {
        assertThat(IpAddresses.parse("not-an-address")).isEmpty();
        assertThat(IpAddresses.parse("999.999.999.999")).isEmpty();
        assertThat(IpAddresses.parse("203.0.113")).isEmpty();
        assertThat(IpAddresses.parse("203.0.113.7.8")).isEmpty();
        assertThat(IpAddresses.parse("203.0.113.")).isEmpty();
        assertThat(IpAddresses.parse("")).isEmpty();
        assertThat(IpAddresses.parse("   ")).isEmpty();
        assertThat(IpAddresses.parse("2001:zzzz::1")).isEmpty();
    }

    @Test
    @DisplayName("a player name is never mistaken for an address")
    void rejectsPlayerNames() {
        assertThat(IpAddresses.parse("Steve")).isEmpty();
        assertThat(IpAddresses.parse("Notch_123")).isEmpty();
    }

    @Test
    @DisplayName("an address that would need escaping in a URL path is rejected outright")
    void rejectsAddressesThatWouldNeedEscaping() {
        assertThat(IpAddresses.parse("fe80::1%eth0")).isEmpty();
        assertThat(IpAddresses.parse("203.0.113.7; DROP TABLE punishments")).isEmpty();
        assertThat(IpAddresses.parse("203.0.113.7/24")).isEmpty();
    }

    @Test
    @DisplayName("a host name that resolves is still not a literal address")
    void rejectsHostNames() {
        assertThat(IpAddresses.parse("localhost")).isEmpty();
        assertThat(IpAddresses.parse("example.com")).isEmpty();
    }

}
