package net.vanfek.proxybans.paper.command.lookup;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.vanfek.proxybans.api.ActivePunishmentView;
import net.vanfek.proxybans.api.PunishmentKind;
import net.vanfek.proxybans.api.platform.Actor;
import net.vanfek.proxybans.core.duration.DurationFormatter;
import net.vanfek.proxybans.core.i18n.Placeholders;
import net.vanfek.proxybans.core.permission.PermissionNodes;
import net.vanfek.proxybans.paper.command.support.CommandContext;
import net.vanfek.proxybans.paper.command.support.Feedback;
import net.vanfek.proxybans.paper.command.support.KindArgument;
import net.vanfek.proxybans.paper.command.support.PlayerSuggestions;
import net.vanfek.proxybans.paper.platform.PaperActor;
import org.bukkit.Server;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class InfoCommand {

    private InfoCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(CommandContext ctx, Server server, String literal) {
        return Commands.literal(literal)
                .requires(source -> ctx.permissions().allows(PaperActor.of(source.getSender()), PermissionNodes.INFO))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> PlayerSuggestions.matching(server, builder))
                        .executes(context -> execute(
                                ctx, PaperActor.of(context.getSource().getSender()),
                                StringArgumentType.getString(context, "player"), null
                        ))
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((context, builder) -> KindArgument.matching(builder))
                                .executes(context -> executeForKind(
                                        ctx, PaperActor.of(context.getSource().getSender()),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "kind")
                                ))))
                .build();
    }

    private static int executeForKind(CommandContext ctx, Actor actor, String playerName, String kindToken) {
        Optional<PunishmentKind> kind = KindArgument.parse(kindToken);

        if (kind.isEmpty()) {
            Feedback.tell(ctx, actor, "errors.bad-kind", Placeholders.of("value", kindToken));

            return Command.SINGLE_SUCCESS;
        }

        return execute(ctx, actor, playerName, kind.get());
    }

    private static int execute(CommandContext ctx, Actor actor, String playerName, @Nullable PunishmentKind kind) {
        ctx.punishments().resolveUuid(playerName)
                .thenCompose(resolved -> resolved
                        .map(uuid -> describe(ctx, actor, playerName, uuid, kind))
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

    private static CompletableFuture<Void> describe(
            CommandContext ctx, Actor actor,
            String playerName, UUID uuid, @Nullable PunishmentKind kind
    ) {
        if (kind != null) {
            return describeKind(ctx, actor, playerName, uuid, kind);
        }

        return describeKind(ctx, actor, playerName, uuid, PunishmentKind.BAN)
                .thenCompose(ignored -> describeKind(ctx, actor, playerName, uuid, PunishmentKind.MUTE));
    }

    private static CompletableFuture<Void> describeKind(
            CommandContext ctx, Actor actor,
            String playerName, UUID uuid, PunishmentKind kind
    ) {
        return ctx.punishments().findActive(uuid, kind).thenAccept(active -> {
            if (active.isEmpty()) {
                Feedback.tell(ctx, actor, emptyKey(kind), Placeholders.of("player", playerName));

                return;
            }

            render(ctx, actor, playerName, active.get());
        });
    }

    private static void render(CommandContext ctx, Actor actor, String playerName, ActivePunishmentView punishment) {
        Instant now = ctx.clock().instant();

        Duration remaining = punishment.expiry()
                .map(expiry -> Duration.between(now, expiry))
                .orElse(Duration.ZERO);

        Feedback.tell(
                ctx, actor, activeKey(punishment),
                Placeholders.of("player", playerName), Placeholders.of("reason", punishment.reason()),
                Placeholders.of("admin", punishment.issuedByName()), Placeholders.of("issued_at", punishment.issuedAt()),
                Placeholders.of("expires_at", punishment.expiry().map(Instant::toString).orElse("—")),
                Placeholders.of("remaining", punishment.isPermanent() ? "—" : DurationFormatter.human(remaining))
        );
    }

    private static String emptyKey(PunishmentKind kind) {
        return kind == PunishmentKind.MUTE ? "info.no-mute" : "info.no-ban";
    }

    private static String activeKey(ActivePunishmentView punishment) {
        if (punishment.isMute()) {
            return punishment.isPermanent() ? "info.muted-permanent" : "info.muted-temporary";
        }

        return punishment.isPermanent() ? "info.banned-permanent" : "info.banned-temporary";
    }

}
