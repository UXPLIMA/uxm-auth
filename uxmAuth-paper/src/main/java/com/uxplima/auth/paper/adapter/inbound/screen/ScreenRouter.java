package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;

/**
 * A Bedrock player is shown the native form, a Java player the dialog.
 *
 * <p>A Java client older than dialogs is shown nothing: it waits in the frozen world with the commands, whatever
 * the operator chose. A connection still before the world is not a player yet, and the gate that let it there
 * asked its version already.
 */
public final class ScreenRouter implements ScreenShower {

    private static final ScreenShower NOTHING = new ScreenShower() {
        @Override
        public void show(Audience who, DialogSpec spec) {}

        @Override
        public void close(Audience who) {}
    };

    private final ScreenShower dialogs;
    private final ScreenShower forms;
    private final Predicate<UUID> bedrock;
    private final Predicate<UUID> drawsDialogs;

    public ScreenRouter(ScreenShower dialogs, ScreenShower forms, Predicate<UUID> bedrock) {
        this(dialogs, forms, bedrock, id -> true);
    }

    /** @param drawsDialogs whether a Java client is new enough to draw a dialog */
    public ScreenRouter(
            ScreenShower dialogs, ScreenShower forms, Predicate<UUID> bedrock, Predicate<UUID> drawsDialogs) {
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
        this.forms = Objects.requireNonNull(forms, "forms");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
        this.drawsDialogs = Objects.requireNonNull(drawsDialogs, "drawsDialogs");
    }

    @Override
    public void show(Audience who, DialogSpec spec) {
        to(who).show(who, spec);
    }

    @Override
    public void close(Audience who) {
        to(who).close(who);
    }

    private ScreenShower to(Audience who) {
        if (!(who instanceof Player player)) {
            return dialogs;
        }
        if (bedrock.test(player.getUniqueId())) {
            return forms;
        }
        return drawsDialogs.test(player.getUniqueId()) ? dialogs : NOTHING;
    }
}
