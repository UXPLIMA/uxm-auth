package com.uxplima.auth.paper.bootstrap;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.staff.AccountInfo;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.staff.InfoForm;
import com.uxplima.auth.paper.adapter.inbound.staff.InfoMenu;
import com.uxplima.auth.paper.adapter.inbound.staff.StaffView;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.common.Log;
import com.uxplima.uxmlib.gui.CatalogueWords;
import com.uxplima.uxmlib.menu.MenuBasics;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.runtime.MenuListener;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;

/**
 * What staff read of an account: lines in the console, the window of {@code menus/info.conf} for a Java player, and
 * a form for a Bedrock one. The window engine is built here, the one place this plugin draws a chest window.
 */
final class StaffGraph implements AutoCloseable {

    final StaffView view;

    private final Plugin plugin;
    private final Menus menus;
    private final MenuListener clicks;
    private final InfoMenu window;

    StaffGraph(
            Plugin plugin,
            Scheduler scheduler,
            Messages messages,
            Styler styler,
            AccountInfo info,
            HashingLane lane,
            Replies replies,
            Supplier<DateTimeFormatter> dates,
            Predicate<UUID> bedrock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        MenuBindings bindings = new MenuBindings();
        MenuRenderer renderer = new MenuRenderer(
                new ItemRenderer(new CatalogueWords(messages, styler), styler::theme, bindings.placeholders()),
                bindings.conditions());
        // The detector and the screen let the engine draw a form for a Bedrock player who reaches the window some
        // other way. The info view itself sends a Bedrock player the form below first.
        this.menus = new Menus(
                renderer,
                scheduler,
                bindings.lists(),
                null,
                bindings.actions(),
                bindings.conditions(),
                null,
                BedrockDetector.forServer(plugin.getServer()),
                BedrockScreen.forServer(plugin.getServer()));
        MenuBasics.register(bindings, menus);
        this.clicks = new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        this.window = new InfoMenu(bindings, menus, messages, dates, this::reread);
        InfoForm form =
                new InfoForm(BedrockScreen.forServer(plugin.getServer()), bedrock, messages, scheduler, this::reread);
        this.view = new StaffView(info, lane, replies, messages, dates, form.orElse(window));
    }

    /** Reads another account into the window or the form, for the staff player who picked it. */
    private void reread(Player viewer, String name) {
        var unused = view.info(viewer, name);
    }

    /** Listens for clicks and reads the window file. */
    void start() {
        clicks.install();
        readWindows();
    }

    /** Reads the window file again, for a reload. */
    void readWindows() {
        Path folder = plugin.getDataFolder().toPath();
        window.read(folder, Log.of(plugin.getLogger()));
    }

    /** How many window files were read, for the doctor. */
    int windowsRead() {
        return window.isRead() ? 1 : 0;
    }

    @Override
    public void close() {
        clicks.uninstall();
        menus.shutdown();
    }
}
