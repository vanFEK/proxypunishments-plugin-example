package net.vanfek.proxybans.velocity.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.vanfek.proxybans.api.ActiveBanView;
import net.vanfek.proxybans.core.duration.DurationFormatter;
import net.vanfek.proxybans.core.i18n.Placeholders;
import net.vanfek.proxybans.core.permission.PermissionNodes;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BanInfoCommand {

    private BanInfoCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, ProxyServer proxy, String literal) {
        LiteralArgumentBuilder<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal(literal)
                .requires(source -> ctx.permissions().allows(source, PermissionNodes.INFO))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> CommandUtils.suggestPlayers(proxy, builder))
                        .executes(context -> execute(
                                ctx,
                                context.getSource(),
                                StringArgumentType.getString(context, "player")
                        )));

        return new BrigadierCommand(node);
    }

    private static int execute(CommandContext ctx, CommandSource source, String playerName) {
        ctx.bans().resolveUuid(playerName)
                .thenCompose(resolved -> resolved
                        .map(uuid -> describe(ctx, source, playerName, uuid))
                        .orElseGet(() -> {
                            CommandUtils.tell(ctx, source, "errors.unknown-player", Placeholders.of("player", playerName));

                            return CompletableFuture.completedFuture(null);
                        }))
                .exceptionally(throwable -> {
                    CommandUtils.reportFailure(ctx, source, throwable);

                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Void> describe(CommandContext ctx, CommandSource source, String playerName, UUID uuid) {
        return ctx.bans().findActiveBan(uuid).thenAccept(active -> {
            if (active.isEmpty()) {
                CommandUtils.tell(ctx, source, "info.no-ban", Placeholders.of("player", playerName));

                return;
            }

            ActiveBanView ban = active.get();
            Instant now = ctx.clock().instant();

            Duration remaining = ban.expiry()
                    .map(expiry -> Duration.between(now, expiry))
                    .orElse(Duration.ZERO);

            String key = ban.isPermanent() ? "info.banned-permanent" : "info.banned-temporary";

            CommandUtils.tell(
                    ctx,
                    source,
                    key,
                    Placeholders.of("player", playerName),
                    Placeholders.of("reason", ban.reason()),
                    Placeholders.of("admin", ban.issuedByName()),
                    Placeholders.of("issued_at", ban.issuedAt()),
                    Placeholders.of("expires_at", ban.expiry().map(Instant::toString).orElse("—")),
                    Placeholders.of("remaining", ban.isPermanent() ? "—" : DurationFormatter.human(remaining))
            );
        });
    }

}
