package com.uxplima.auth.paper.adapter.inbound.staff;

import java.util.List;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.auth.application.staff.AccountInfo;

/** A view of an account drawn for a player, in place of the lines the console reads. */
@FunctionalInterface
public interface InfoWindow {

    /** Draws nothing, so every player reads the lines. */
    InfoWindow NONE = (viewer, view, lines) -> false;

    /**
     * Shows the account to the viewer, on the viewer's own thread.
     *
     * @param lines the lines the console reads of the same account, for a window that shows them as they are
     * @return false when this window has nothing to draw for the viewer, who then reads the lines
     */
    boolean show(Player viewer, AccountInfo.View view, List<Component> lines);

    /** This window, and the other one for a viewer this one draws nothing for. */
    default InfoWindow orElse(InfoWindow other) {
        return (viewer, view, lines) -> show(viewer, view, lines) || other.show(viewer, view, lines);
    }
}
