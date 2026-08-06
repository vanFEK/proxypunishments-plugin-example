package net.vanfek.proxypunishments.velocity.command.admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.vanfek.proxypunishments.api.platform.Actor;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import net.vanfek.proxypunishments.core.permission.PermissionNodes;
import net.vanfek.proxypunishments.core.reload.BoundSetting;
import net.vanfek.proxypunishments.core.reload.ReloadOutcome;
import net.vanfek.proxypunishments.velocity.command.support.CommandContext;
import net.vanfek.proxypunishments.velocity.command.support.Feedback;
import net.vanfek.proxypunishments.velocity.platform.VelocityActor;

public final class ReloadCommand {

    private ReloadCommand() {
    }

    public static BrigadierCommand build(CommandContext ctx, String literal) {
        LiteralArgumentBuilder<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal(literal)
                .requires(source -> ctx.permissions().allows(VelocityActor.of(source), PermissionNodes.RELOAD))
                .executes(context -> execute(ctx, VelocityActor.of(context.getSource())));

        return new BrigadierCommand(node);
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
