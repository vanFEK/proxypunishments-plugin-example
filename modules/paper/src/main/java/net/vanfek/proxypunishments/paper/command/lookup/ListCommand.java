package net.vanfek.proxypunishments.paper.command.lookup;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.vanfek.proxypunishments.api.ActivePunishmentView;
import net.vanfek.proxypunishments.api.PunishmentKind;
import net.vanfek.proxypunishments.api.platform.Actor;
import net.vanfek.proxypunishments.core.duration.DurationFormatter;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import net.vanfek.proxypunishments.core.permission.PermissionNodes;
import net.vanfek.proxypunishments.paper.command.support.CommandContext;
import net.vanfek.proxypunishments.paper.command.support.Feedback;
import net.vanfek.proxypunishments.paper.command.support.KindArgument;
import net.vanfek.proxypunishments.paper.platform.PaperActor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class ListCommand {

    private static final int PAGE_SIZE = 10;
    private static final int MAX_PAGE = 1_000;

    private ListCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(CommandContext ctx, String literal) {
        return Commands.literal(literal)
                .requires(source -> ctx.permissions().allows(PaperActor.of(source.getSender()), PermissionNodes.LIST))
                .executes(context -> execute(ctx, PaperActor.of(context.getSource().getSender()), PunishmentKind.BAN, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1, MAX_PAGE))
                        .executes(context -> execute(
                                ctx, PaperActor.of(context.getSource().getSender()),
                                PunishmentKind.BAN, IntegerArgumentType.getInteger(context, "page")
                        )))
                .then(Commands.argument("kind", StringArgumentType.word())
                        .suggests((context, builder) -> KindArgument.matching(builder))
                        .executes(context -> executeForKind(
                                ctx, PaperActor.of(context.getSource().getSender()),
                                StringArgumentType.getString(context, "kind"), 1
                        ))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1, MAX_PAGE))
                                .executes(context -> executeForKind(
                                        ctx, PaperActor.of(context.getSource().getSender()),
                                        StringArgumentType.getString(context, "kind"),
                                        IntegerArgumentType.getInteger(context, "page")
                                ))))
                .build();
    }

    private static int executeForKind(CommandContext ctx, Actor actor, String kindToken, int page) {
        Optional<PunishmentKind> kind = KindArgument.parse(kindToken);

        if (kind.isEmpty()) {
            Feedback.tell(ctx, actor, "errors.bad-kind", Placeholders.of("value", kindToken));

            return Command.SINGLE_SUCCESS;
        }

        return execute(ctx, actor, kind.get(), page);
    }

    private static int execute(CommandContext ctx, Actor actor, PunishmentKind kind, int page) {
        int offset = (page - 1) * PAGE_SIZE;

        ctx.punishments().listActive(kind, PAGE_SIZE, offset)
                .thenAccept(punishments -> render(ctx, actor, kind, page, punishments))
                .exceptionally(throwable -> {
                    Feedback.reportFailure(ctx, actor, throwable);

                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void render(
            CommandContext ctx, Actor actor,
            PunishmentKind kind, int page, List<ActivePunishmentView> punishments
    ) {
        boolean mutes = kind == PunishmentKind.MUTE;

        if (punishments.isEmpty()) {
            Feedback.tell(ctx, actor, mutes ? "list.empty-mutes" : "list.empty");

            return;
        }

        Feedback.tell(ctx, actor, mutes ? "list.header-mutes" : "list.header", Placeholders.of("page", page));

        Instant now = ctx.clock().instant();

        for (ActivePunishmentView punishment : punishments) {
            String key = punishment.isPermanent() ? "list.entry-permanent" : "list.entry-temporary";

            String remaining = punishment.expiry()
                    .filter(expiry -> !punishment.isPermanent())
                    .map(expiry -> DurationFormatter.human(Duration.between(now, expiry)))
                    .orElse("—");

            Feedback.tell(
                    ctx, actor, key,
                    Placeholders.of("player", punishment.displayName()), Placeholders.of("reason", punishment.reason()),
                    Placeholders.of("admin", punishment.issuedByName()), Placeholders.of("remaining", remaining)
            );
        }
    }

}
