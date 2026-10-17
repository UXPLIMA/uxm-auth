package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;

import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.Nullable;

/** Hands a press on one of this plugin's screens, by a player in the world, to the answers. */
public final class ScreenClickListener implements Listener {

    private final ScreenAnswers answers;
    private final Scheduler scheduler;

    public ScreenClickListener(ScreenAnswers answers, Scheduler scheduler) {
        this.answers = Objects.requireNonNull(answers, "answers");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler
    public void onClick(PlayerCustomClickEvent event) {
        if (!PaperScreens.NAMESPACE.equals(event.getIdentifier().namespace())
                || !(event.getCommonConnection() instanceof PlayerGameConnection game)) {
            return;
        }
        Player player = game.getPlayer();
        String action = event.getIdentifier().value();
        ScreenAnswers.Answer answer = answerOf(event.getDialogResponseView());
        // The press may arrive off the player's thread; everything it does to the player happens on it.
        scheduler.entity(player, () -> {
            CompletableFuture<Void> unused = answers.answer(player, action, answer);
        });
    }

    /** The fields as the client sent them, read once, so nothing holds the response after the event. */
    static ScreenAnswers.Answer answerOf(@Nullable DialogResponseView view) {
        return new ScreenAnswers.Answer() {
            @Override
            public @Nullable String text(String key) {
                return view == null ? null : view.getText(key);
            }

            @Override
            public boolean ticked(String key) {
                return view != null && Boolean.TRUE.equals(view.getBoolean(key));
            }
        };
    }
}
