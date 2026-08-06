package net.vanfek.proxypunishments.paper.command.ip;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.vanfek.proxypunishments.api.AltView;
import net.vanfek.proxypunishments.api.platform.Actor;
import net.vanfek.proxypunishments.core.i18n.Placeholders;
import net.vanfek.proxypunishments.core.permission.PermissionNodes;
import net.vanfek.proxypunishments.paper.command.support.CommandContext;
import net.vanfek.proxypunishments.paper.command.support.Feedback;
import net.vanfek.proxypunishments.paper.command.support.PlayerSuggestions;
import net.vanfek.proxypunishments.paper.platform.PaperActor;
import org.bukkit.Server;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AltsCommand {

    private static final int PAGE_SIZE = 20;

    private AltsCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(CommandContext ctx, Server server, String literal) {
        return Commands.literal(literal)
                .requires(source -> ctx.permissions().allows(PaperActor.of(source.getSender()), PermissionNodes.ALTS))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> PlayerSuggestions.matching(server, builder))
                        .executes(context -> execute(
                                ctx, PaperActor.of(context.getSource().getSender()),
                                StringArgumentType.getString(context, "player")
                        )))
                .build();
    }

    private static int execute(CommandContext ctx, Actor actor, String playerName) {
        ctx.punishments().resolveUuid(playerName)
                .thenCompose(resolved -> resolved
                        .map(uuid -> print(ctx, actor, playerName, uuid))
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

    private static CompletableFuture<Void> print(CommandContext ctx, Actor actor, String playerName, UUID uuid) {
        return ctx.punishments().alts(uuid, PAGE_SIZE).thenAccept(alts -> {
            if (alts.isEmpty()) {
                Feedback.tell(ctx, actor, "alts.empty", Placeholders.of("player", playerName));

                return;
            }

            Feedback.tell(ctx, actor, "alts.header", Placeholders.of("player", playerName));

            renderEntries(ctx, actor, alts);
        });
    }

    private static void renderEntries(CommandContext ctx, Actor actor, List<AltView> alts) {
        for (AltView alt : alts) {
            Feedback.tell(
                    ctx, actor, "alts.entry",
                    Placeholders.of("alt", alt.login()), Placeholders.of("last_seen", alt.lastSeenAt())
            );
        }
    }

}
