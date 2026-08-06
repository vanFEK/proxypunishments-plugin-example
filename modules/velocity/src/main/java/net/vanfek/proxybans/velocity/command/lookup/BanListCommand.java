package net.vanfek.proxybans.velocity.command.lookup;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.vanfek.proxybans.api.ActiveBanView;
import net.vanfek.proxybans.core.duration.DurationFormatter;
import net.vanfek.proxybans.core.i18n.Placeholders;
import net.vanfek.proxybans.core.permission.PermissionNodes;
import net.vanfek.proxybans.velocity.command.support.CommandContext;
import net.vanfek.proxybans.velocity.command.support.Feedback;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class BanListCommand {

    private static final int PAGE_SIZE = 10;
    private static final int MAX_PAGE = 1_000;

    private BanListCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, String literal) {
        LiteralArgumentBuilder<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal(literal)
                .requires(source -> ctx.permissions().allows(source, PermissionNodes.LIST))
                .executes(context -> execute(ctx, context.getSource(), 1))
                .then(RequiredArgumentBuilder.<CommandSource, Integer>argument("page", IntegerArgumentType.integer(1, MAX_PAGE))
                        .executes(context -> execute(
                                ctx, context.getSource(),
                                IntegerArgumentType.getInteger(context, "page")
                        )));

        return new BrigadierCommand(node);
    }

    private static int execute(CommandContext ctx, CommandSource source, int page) {
        int offset = (page - 1) * PAGE_SIZE;

        ctx.bans().listActive(PAGE_SIZE, offset)
                .thenAccept(bans -> render(ctx, source, page, bans))
                .exceptionally(throwable -> {
                    Feedback.reportFailure(ctx, source, throwable);

                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void render(CommandContext ctx, CommandSource source, int page, List<ActiveBanView> bans) {
        if (bans.isEmpty()) {
            Feedback.tell(ctx, source, "list.empty");

            return;
        }

        Feedback.tell(ctx, source, "list.header", Placeholders.of("page", page));

        Instant now = ctx.clock().instant();

        for (ActiveBanView ban : bans) {
            String key = ban.isPermanent() ? "list.entry-permanent" : "list.entry-temporary";

            String remaining = ban.expiry()
                    .filter(expiry -> !ban.isPermanent())
                    .map(expiry -> DurationFormatter.human(Duration.between(now, expiry)))
                    .orElse("—");

            Feedback.tell(
                    ctx, source, key,
                    Placeholders.of("player", ban.displayName()), Placeholders.of("reason", ban.reason()),
                    Placeholders.of("admin", ban.issuedByName()), Placeholders.of("remaining", remaining)
            );
        }
    }

}
