package net.vanfek.proxypunishments.velocity.command.lookup;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.vanfek.proxypunishments.api.ActivePunishmentView;
import net.vanfek.proxypunishments.api.PunishmentKind;
import net.vanfek.proxypunishments.api.platform.Actor;
import net.vanfek.proxypunishments.core.duration.DurationFormatter;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import net.vanfek.proxypunishments.core.permission.PermissionNodes;
import net.vanfek.proxypunishments.velocity.command.support.CommandContext;
import net.vanfek.proxypunishments.velocity.command.support.Feedback;
import net.vanfek.proxypunishments.velocity.command.support.KindArgument;
import net.vanfek.proxypunishments.velocity.platform.VelocityActor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class ListCommand {

    private static final int PAGE_SIZE = 10;
    private static final int MAX_PAGE = 1_000;

    private ListCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, String literal) {
        LiteralArgumentBuilder<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal(literal)
                .requires(source -> ctx.permissions().allows(VelocityActor.of(source), PermissionNodes.LIST))
                .executes(context -> execute(ctx, VelocityActor.of(context.getSource()), PunishmentKind.BAN, 1))
                .then(RequiredArgumentBuilder.<CommandSource, Integer>argument("page", IntegerArgumentType.integer(1, MAX_PAGE))
                        .executes(context -> execute(
                                ctx, VelocityActor.of(context.getSource()),
                                PunishmentKind.BAN, IntegerArgumentType.getInteger(context, "page")
                        )))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("kind", StringArgumentType.word())
                        .suggests((context, builder) -> KindArgument.matching(builder))
                        .executes(context -> executeForKind(
                                ctx, VelocityActor.of(context.getSource()),
                                StringArgumentType.getString(context, "kind"), 1
                        ))
                        .then(RequiredArgumentBuilder.<CommandSource, Integer>argument("page", IntegerArgumentType.integer(1, MAX_PAGE))
                                .executes(context -> executeForKind(
                                        ctx, VelocityActor.of(context.getSource()),
                                        StringArgumentType.getString(context, "kind"),
                                        IntegerArgumentType.getInteger(context, "page")
                                ))));

        return new BrigadierCommand(node);
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
