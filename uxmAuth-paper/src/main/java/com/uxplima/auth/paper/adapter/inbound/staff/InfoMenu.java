package com.uxplima.auth.paper.adapter.inbound.staff;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.application.staff.AccountInfo;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.uxmlib.common.Log;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.spec.MenuSpecs;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * The info view on Java, which is {@code menus/info.conf}.
 *
 * <p>The file says where every tile sits, what it is made of and which facts it draws; what is left here is the
 * facts themselves, read off the view the lane already read, so drawing the window touches no table. A click on
 * another account reads that account, through the same command path, into the same window.
 *
 * <p>An operator who deletes the file takes the window away, and staff read the lines instead.
 */
public final class InfoMenu implements InfoWindow {

    /** The window this is, which is the name of its file under {@code menus/}. */
    public static final String MENU = "info";

    private static final String RESOURCE = "menus/info.conf";

    /** The rows of a window whose file cannot be read at all. */
    private static final int ROWS = 5;

    private static final String LOGINS = "uxmauth:logins";
    private static final String ALTS = "uxmauth:alts";
    private static final String NO_LOGINS = "uxmauth:no-logins";
    private static final String NO_ALTS = "uxmauth:no-alts";
    private static final String OPEN_ALT = "uxmauth:open-alt";

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Menus menus;
    private final Words words;
    private final Supplier<DateTimeFormatter> dates;
    private final BiConsumer<Player, String> openAlt;

    /**
     * @param dates how a date reads to staff, in the zone it is shown in
     * @param openAlt reads the account under a name for the player who clicked it
     */
    public InfoMenu(
            MenuBindings bindings,
            Menus menus,
            Messages messages,
            Supplier<DateTimeFormatter> dates,
            BiConsumer<Player, String> openAlt) {
        Objects.requireNonNull(bindings, "bindings");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.words = new Words(Objects.requireNonNull(messages, "messages"));
        this.dates = Objects.requireNonNull(dates, "dates");
        this.openAlt = Objects.requireNonNull(openAlt, "openAlt");
        bindings.list(LOGINS, context -> view(context).recent());
        bindings.list(ALTS, context -> view(context).alts());
        bindings.condition(NO_LOGINS, (context, args) -> view(context).recent().isEmpty());
        bindings.condition(NO_ALTS, (context, args) -> view(context).alts().isEmpty());
        bindings.action(
                OPEN_ALT,
                click -> this.openAlt.accept(
                        click.player(), click.entry(PlayerName.class).value()));
        facts(bindings);
        entries(bindings);
    }

    /** Reads the window from the data folder, or forgets it when the file is gone. */
    public void read(Path dataFolder, Log log) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        if (!Files.isRegularFile(dataFolder.resolve(RESOURCE))) {
            menus.unregisterSpec(MENU);
            return;
        }
        menus.registerSpec(MENU, MenuSpecs.loadOrBundled(RESOURCE, dataFolder, ROWS, log));
    }

    /** Whether the window file was read, for the doctor. */
    public boolean isRead() {
        return menus.registeredSpec(MENU).isPresent();
    }

    @Override
    public boolean show(Player viewer, AccountInfo.View view, List<Component> lines) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(view, "view");
        if (!isRead()) {
            return false;
        }
        menus.open(viewer, MENU, view);
        return true;
    }

    private void facts(MenuBindings bindings) {
        bindings.placeholder("info_name", context -> account(context).name().value());
        bindings.placeholder(
                "info_kind",
                context -> words.of(context, StaffView.kind(account(context).kind())));
        bindings.placeholder("info_registered", context -> date(account(context).registeredAt()));
        bindings.placeholder("info_origin", context -> account(context).registrationAddress());
        bindings.placeholder(
                "info_last",
                context -> view(context).lastLogin().map(this::date).orElseGet(() -> words.none(context)));
        bindings.placeholder("info_seen", context -> view(context).lastAddress().orElseGet(() -> words.none(context)));
        bindings.placeholder(
                "info_logins", context -> String.valueOf(view(context).logins()));
        bindings.placeholder(
                "info_app", context -> words.onOff(context, view(context).app()));
        bindings.placeholder(
                "info_backup", context -> String.valueOf(view(context).backupCodes()));
        bindings.placeholder(
                "info_mail", context -> words.onOff(context, view(context).mailedCode()));
        bindings.placeholder("info_address", context -> view(context).address().orElseGet(() -> words.none(context)));
    }

    private void entries(MenuBindings bindings) {
        bindings.placeholder("login_date", context -> date(login(context).at()));
        bindings.placeholder("login_address", context -> login(context).address());
        bindings.placeholder(
                "login_method",
                context -> words.of(context, StaffView.method(login(context).method())));
        bindings.placeholder(
                "alt_name", context -> context.entry(PlayerName.class).value());
    }

    private String date(Instant at) {
        return dates.get().format(at);
    }

    private static AccountInfo.View view(MenuContext context) {
        return context.subject(AccountInfo.View.class);
    }

    private static Account account(MenuContext context) {
        return view(context).account();
    }

    private static LoginHistory.Entry login(MenuContext context) {
        return context.entry(LoginHistory.Entry.class);
    }

    /** A word of the catalogue as plain text, because a tile takes a value as text and never as markup. */
    private record Words(Messages catalogue) {

        String of(MenuContext context, Keys key) {
            return PLAIN.serialize(catalogue.render(context.viewer(), key));
        }

        String none(MenuContext context) {
            return of(context, Keys.MENU_INFO_NONE);
        }

        String onOff(MenuContext context, boolean on) {
            return of(context, on ? Keys.INFO_ON : Keys.INFO_OFF);
        }
    }
}
