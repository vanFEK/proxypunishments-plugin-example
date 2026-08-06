# Changelog

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `/pp reload` on both platforms: re-reads `config.yml` and `messages.yml` and swaps both in at once,
  or neither when one of them fails to parse. The reply names every setting that is bound at startup
  and therefore still needs a restart, with its running and its configured value.

## [1.0.0]

### Added

- Velocity module: ban, mute and warning commands, login enforcement, `/pp` root command with
  top-level aliases that step aside when another plugin already owns the name.
- Paper module: the same commands plus mute enforcement in chat, which a proxy cannot do reliably
  since signed chat landed in 1.19. Detects proxy forwarding and lets the proxy own the commands.
- IP bans and alt lookup: `/ipban`, `/unipban`, `/alts`, with the connecting address checked at login.
- Two-tier cache with a stale fallback, and a configurable fail-open policy for when the service is
  unreachable.
- Optional Redis subscriber so a punishment issued on one proxy invalidates every other proxy's
  cache immediately instead of within the TTL.
- Platform-neutral `api` and `core` modules: neither imports Velocity or Bukkit.
- `/pp status` for cache hit ratio and service latency.
- MiniMessage templates with unparsed placeholders, so a ban reason cannot smuggle markup into a
  staff message, and bundled defaults behind the operator's file so new keys always resolve.

### Notes

- Built on the JDK 25 toolchain, targeting Java 21 bytecode, so the jars load on the servers people
  actually run.
