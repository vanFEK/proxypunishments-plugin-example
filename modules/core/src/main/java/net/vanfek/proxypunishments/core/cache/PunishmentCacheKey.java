package net.vanfek.proxypunishments.core.cache;

// Sealed so an address entry can never be looked up, or overwritten, through a player key.
public sealed interface PunishmentCacheKey permits PunishmentKey, IpBanKey {
}
