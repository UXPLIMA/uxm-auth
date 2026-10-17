package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;

/** A Bedrock player is shown the native form, everyone else the dialog. */
public final class ScreenRouter implements ScreenShower {

    private final ScreenShower dialogs;
    private final ScreenShower forms;
    private final Predicate<UUID> bedrock;

    public ScreenRouter(ScreenShower dialogs, ScreenShower forms, Predicate<UUID> bedrock) {
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
        this.forms = Objects.requireNonNull(forms, "forms");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
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
        return who instanceof Player player && bedrock.test(player.getUniqueId()) ? forms : dialogs;
    }
}
