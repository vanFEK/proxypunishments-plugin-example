package net.vanfek.proxypunishments.paper.command.mute;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.vanfek.proxypunishments.api.PunishmentKind;
import net.vanfek.proxypunishments.api.platform.Actor;
import net.vanfek.proxypunishments.core.duration.DurationFormatter;
import net.vanfek.proxypunishments.core.duration.DurationParser;
import net.vanfek.proxypunishments.core.duration.ParsedDuration;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import net.vanfek.proxypunishments.core.permission.PermissionNodes;
import net.vanfek.proxypunishments.paper.command.support.CommandContext;
import net.vanfek.proxypunishments.paper.command.support.Feedback;
import net.vanfek.proxypunishments.paper.command.support.PlayerSuggestions;
import net.vanfek.proxypunishments.paper.platform.PaperActor;
import org.bukkit.Server;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class TempMuteCommand {

    private TempMuteCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(CommandContext ctx, Server server, String literal) {
        return Commands.literal(literal)
                .requires(source -> ctx.permissions().allows(PaperActor.of(source.getSender()), PermissionNodes.TEMPMUTE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> PlayerSuggestions.matching(server, builder))
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> execute(
                                                ctx, PaperActor.of(context.getSource().getSender()),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "duration"),
                                                StringArgumentType.getString(context, "reason")
                                        )))))
                .build();
    }

    private static int execute(CommandContext ctx, Actor actor, String playerName, String durationToken, String reason) {
        ParsedDuration parsed;

        try {
            parsed = DurationParser.parse(durationToken);
        } catch (IllegalArgumentException ex) {
            Feedback.tell(ctx, actor, "errors.bad-duration", Placeholders.of("value", durationToken));

            return Command.SINGLE_SUCCESS;
        }

        if (parsed.isPermanent()) {
            Feedback.tell(ctx, actor, "errors.tempmute-requires-duration");

            return Command.SINGLE_SUCCESS;
        }

        if (reason.isBlank()) {
            Feedback.tell(ctx, actor, "errors.reason-required");

            return Command.SINGLE_SUCCESS;
        }

        Duration duration = parsed.duration();

        ctx.punishments().resolveUuid(playerName)
                .thenCompose(resolved -> resolved
                        .map(uuid -> issue(ctx, actor, playerName, uuid, reason, duration))
                        .orElseGet(() -> {
                            Feedback.tell(ctx, actor, "errors.unknown-player", Placeholders.of("player", playerName));

                            return CompletableFuture.completedFuture(null);
                        }))
                .exceptionally(throwable -> {
                    Feedback.reportFailure(ctx, actor, throwable);

                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Void> issue(
            CommandContext ctx, Actor actor,
            String playerName, UUID uuid,
            String reason, Duration duration
    ) {
        return ctx.punishments().issue(uuid, PunishmentKind.MUTE, actor.uuid(), actor.name(), reason, duration)
                .thenAccept(mute -> Feedback.tell(
                        ctx, actor, "mute.success-temporary",
                        Placeholders.of("player", playerName), Placeholders.of("reason", reason),
                        Placeholders.of("duration", DurationFormatter.human(duration))
                ));
    }

}
