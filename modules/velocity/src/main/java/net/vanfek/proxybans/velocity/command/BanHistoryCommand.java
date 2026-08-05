package net.vanfek.proxybans.velocity.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.vanfek.proxybans.api.PunishmentHistoryEntry;
import net.vanfek.proxybans.core.i18n.Placeholders;
import net.vanfek.proxybans.core.permission.PermissionNodes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BanHistoryCommand {

    private static final int PAGE_SIZE = 10;
    private static final int MAX_PAGE = 1_000;

    private BanHistoryCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, ProxyServer proxy, String literal) {
        LiteralArgumentBuilder<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal(literal)
                .requires(source -> ctx.permissions().allows(source, PermissionNodes.HISTORY))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> CommandUtils.suggestPlayers(proxy, builder))
                        .executes(context -> execute(
                                ctx,
                                context.getSource(),
                                StringArgumentType.getString(context, "player"),
                                1
                        ))
                        .then(RequiredArgumentBuilder.<CommandSource, Integer>argument("page", IntegerArgumentType.integer(1, MAX_PAGE))
                                .executes(context -> execute(
                                        ctx,
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        IntegerArgumentType.getInteger(context, "page")
                                ))));

        return new BrigadierCommand(node);
    }

    private static int execute(CommandContext ctx, CommandSource source, String playerName, int page) {
        int offset = (page - 1) * PAGE_SIZE;

        ctx.bans().resolveUuid(playerName)
                .thenCompose(resolved -> resolved
                        .map(uuid -> print(ctx, source, playerName, uuid, page, offset))
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

    private static CompletableFuture<Void> print(
            CommandContext ctx,
            CommandSource source,
            String playerName,
            UUID uuid,
            int page,
            int offset
    ) {
        return ctx.bans().history(uuid, PAGE_SIZE, offset).thenAccept(entries -> {
            if (entries.isEmpty()) {
                CommandUtils.tell(ctx, source, "history.empty", Placeholders.of("player", playerName));

                return;
            }

            CommandUtils.tell(
                    ctx,
                    source,
                    "history.header",
                    Placeholders.of("player", playerName),
                    Placeholders.of("page", page)
            );

            renderEntries(ctx, source, entries);
        });
    }

    private static void renderEntries(CommandContext ctx, CommandSource source, List<PunishmentHistoryEntry> entries) {
        for (PunishmentHistoryEntry entry : entries) {
            CommandUtils.tell(
                    ctx,
                    source,
                    "history.entry",
                    Placeholders.of("action", entry.action().name()),
                    Placeholders.of("admin", entry.issuedByName()),
                    Placeholders.of("reason", entry.reason()),
                    Placeholders.of("at", entry.happenedAt()),
                    Placeholders.of("expires_at", entry.expiry().map(Instant::toString).orElse("—"))
            );
        }
    }

}
