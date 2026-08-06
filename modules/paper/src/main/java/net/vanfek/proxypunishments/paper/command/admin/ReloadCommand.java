package net.vanfek.proxypunishments.paper.command.admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.vanfek.proxypunishments.api.platform.Actor;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import net.vanfek.proxypunishments.core.permission.PermissionNodes;
import net.vanfek.proxypunishments.core.reload.BoundSetting;
import net.vanfek.proxypunishments.core.reload.ReloadOutcome;
import net.vanfek.proxypunishments.paper.command.support.CommandContext;
import net.vanfek.proxypunishments.paper.command.support.Feedback;
import net.vanfek.proxypunishments.paper.platform.PaperActor;

public final class ReloadCommand {

    private ReloadCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(CommandContext ctx, String literal) {
        return Commands.literal(literal)
                .requires(source -> ctx.permissions().allows(PaperActor.of(source.getSender()), PermissionNodes.RELOAD))
                .executes(context -> execute(ctx, PaperActor.of(context.getSource().getSender())))
                .build();
    }

    private static int execute(CommandContext ctx, Actor actor) {
        ReloadOutcome outcome = ctx.reloader().reload();

        if (!outcome.applied()) {
            Feedback.tell(ctx, actor, "reload.failed", Placeholders.of("error", outcome.failure()));

            return Command.SINGLE_SUCCESS;
        }

        Feedback.tell(ctx, actor, "reload.success");

        if (outcome.needingRestart().isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }

        Feedback.tell(ctx, actor, "reload.restart-header");

        for (BoundSetting setting : outcome.needingRestart()) {
            Feedback.tell(
                    ctx, actor, "reload.restart-entry",
                    Placeholders.of("setting", setting.path()), Placeholders.of("running", setting.running()),
                    Placeholders.of("configured", setting.configured())
            );
        }

        return Command.SINGLE_SUCCESS;
    }

}
