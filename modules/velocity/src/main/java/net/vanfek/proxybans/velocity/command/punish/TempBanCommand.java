package net.vanfek.proxybans.velocity.command.punish;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.vanfek.proxybans.core.duration.DurationFormatter;
import net.vanfek.proxybans.core.duration.DurationParser;
import net.vanfek.proxybans.core.duration.ParsedDuration;
import net.vanfek.proxybans.core.i18n.Placeholders;
import net.vanfek.proxybans.core.permission.PermissionNodes;
import net.vanfek.proxybans.velocity.command.support.CommandContext;
import net.vanfek.proxybans.velocity.command.support.Feedback;
import net.vanfek.proxybans.velocity.command.support.Issuer;
import net.vanfek.proxybans.velocity.command.support.PlayerSuggestions;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class TempBanCommand {

    private TempBanCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, ProxyServer proxy, String literal) {
        LiteralArgumentBuilder<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal(literal)
                .requires(source -> ctx.permissions().allows(source, PermissionNodes.TEMPBAN))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> PlayerSuggestions.matching(proxy, builder))
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("duration", StringArgumentType.word())
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> execute(
                                                ctx, context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "duration"),
                                                StringArgumentType.getString(context, "reason")
                                        )))));

        return new BrigadierCommand(node);
    }

    private static int execute(CommandContext ctx, CommandSource source, String playerName, String durationToken, String reason) {
        ParsedDuration parsed;

        try {
            parsed = DurationParser.parse(durationToken);
        } catch (IllegalArgumentException ex) {
            Feedback.tell(ctx, source, "errors.bad-duration", Placeholders.of("value", durationToken));

            return Command.SINGLE_SUCCESS;
        }

        if (parsed.isPermanent()) {
            Feedback.tell(ctx, source, "errors.tempban-requires-duration");

            return Command.SINGLE_SUCCESS;
        }

        if (reason.isBlank()) {
            Feedback.tell(ctx, source, "errors.reason-required");

            return Command.SINGLE_SUCCESS;
        }

        Duration duration = parsed.duration();
        Issuer issuer = Issuer.of(source);

        ctx.bans().resolveUuid(playerName)
                .thenCompose(resolved -> resolved
                        .map(uuid -> issue(ctx, source, playerName, uuid, reason, duration, issuer))
                        .orElseGet(() -> {
                            Feedback.tell(ctx, source, "errors.unknown-player", Placeholders.of("player", playerName));

                            return CompletableFuture.completedFuture(null);
                        }))
                .exceptionally(throwable -> {
                    Feedback.reportFailure(ctx, source, throwable);

                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Void> issue(
            CommandContext ctx, CommandSource source,
            String playerName, UUID uuid,
            String reason, Duration duration, Issuer issuer
    ) {
        return ctx.bans().issueBan(uuid, issuer.uuid(), issuer.name(), reason, duration)
                .thenAccept(ban -> Feedback.tell(
                        ctx, source, "ban.success-temporary",
                        Placeholders.of("player", playerName), Placeholders.of("reason", reason),
                        Placeholders.of("duration", DurationFormatter.human(duration))
                ));
    }

}
