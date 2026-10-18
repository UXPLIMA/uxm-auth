package com.uxplima.auth.paper.adapter.inbound.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.application.staff.AccountInfo;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.common.Log;
import com.uxplima.uxmlib.gui.CatalogueWords;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.runtime.MenuActionContext;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.spec.ClickKind;
import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecs;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** The info window: the shipped file, drawn with the facts of one account, and an alt that opens its own. */
final class InfoMenuTest {

    private static final Instant AT = Instant.parse("2026-10-18T09:05:00Z");

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock staff;
    private MenuBindings bindings;
    private ItemRenderer items;
    private InfoMenu menu;
    private final List<String> opened = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        staff = server.addPlayer("Staff");
        bindings = new MenuBindings();
        Styler styler = new Styler(Theme.defaults());
        items = new ItemRenderer(new CatalogueWords(world.messages, styler), styler::theme, bindings.placeholders());
        Menus menus = new Menus(new MenuRenderer(items, bindings.conditions()), world.scheduler, bindings.lists());
        menu = new InfoMenu(
                bindings,
                menus,
                world.messages,
                () -> DAY,
                (viewer, name) -> opened.add(viewer.getName() + " -> " + name));
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private AccountInfo.View steve() {
        world.registered(server.addPlayer("Steve"), "hunter22");
        world.registered(server.addPlayer("Alex"), "hunter22");
        var steve =
                world.accounts.findByName(new PlayerName("Steve")).orElseThrow().id();
        var alex =
                world.accounts.findByName(new PlayerName("Alex")).orElseThrow().id();
        world.history.record(steve, new LoginHistory.Entry(AT, "203.0.113.7", AuthMethod.PASSWORD));
        world.history.record(alex, new LoginHistory.Entry(AT, "203.0.113.7", AuthMethod.SESSION));
        world.factors.setApp(steve, new TotpSecret(new byte[20]));
        world.emails.setAddress(steve, EmailAddress.parse("steve@example.net").orElseThrow());
        return world.accountInfo().of(new PlayerName("Steve")).orElseThrow();
    }

    private static MenuSpec shipped() {
        return MenuSpecs.loadOrBundled("menus/info.conf", Path.of("no-such-folder"), 5, Log.NONE);
    }

    private MenuContext opening(AccountInfo.View view) {
        return MenuContext.of(staff, view, 0).withLocalPlaceholders(shipped().placeholders());
    }

    /** What a Java player reads off a tile: its tooltip, in plain words. */
    private String tooltip(MenuItemSpec item, MenuContext context) {
        return String.join(
                "\n",
                items.lore(item, context).stream()
                        .map(PlainTextComponentSerializer.plainText()::serialize)
                        .toList());
    }

    private List<?> list(String source, MenuContext context) {
        return bindings.list(source).orElseThrow().apply(context);
    }

    private static MenuItemSpec item(String name) {
        return java.util.Objects.requireNonNull(shipped().items().get(name), name);
    }

    private MenuItemSpec template(String item) {
        return item(item).list().orElseThrow().template();
    }

    @Test
    @DisplayName("the window names the account, its last login, its security, its logins and its alts")
    void theWindowCarriesTheFacts() {
        AccountInfo.View view = steve();
        MenuContext context = opening(view);

        assertThat(tooltip(item("account"), context))
                .contains("a password account")
                .contains(DAY.format(view.account().registeredAt()))
                .contains(view.account().registrationAddress());
        assertThat(tooltip(item("last"), context)).contains("2026-10-18 09:05").contains("203.0.113.7");
        assertThat(tooltip(item("security"), context))
                .contains("App on")
                .contains("Code by mail off")
                .contains("s***@e***.net")
                .doesNotContain("steve@example.net");

        List<?> logins = list("uxmauth:logins", context);
        assertThat(logins).hasSize(1);
        assertThat(tooltip(template("logins"), context.withEntry(logins.getFirst())))
                .contains("2026-10-18 09:05")
                .contains("203.0.113.7")
                .contains("password");
        List<?> alts = list("uxmauth:alts", context);
        assertThat(alts).hasSize(1);
        assertThat(tooltip(template("alts"), context.withEntry(alts.getFirst())))
                .contains("Alex");
    }

    @Test
    @DisplayName("no token and no catalogue path reaches a tile")
    void nothingUnfilledReachesATile() {
        MenuContext context = opening(steve());
        StringBuilder read = new StringBuilder();
        shipped().items().forEach((name, item) -> {
            if (item.list().isEmpty()) {
                read.append(tooltip(item, context)).append('\n');
            }
        });
        read.append(tooltip(
                template("logins"),
                context.withEntry(list("uxmauth:logins", context).getFirst())));
        read.append(tooltip(
                template("alts"),
                context.withEntry(list("uxmauth:alts", context).getFirst())));

        assertThat(read.toString())
                .doesNotContain("%")
                .doesNotContain("menu.info")
                .doesNotContain("<info_")
                .doesNotContain("<login_")
                .doesNotContain("<alt_");
    }

    @Test
    @DisplayName("the empty tiles stand only where there is nothing to list")
    void theEmptyTilesFollowTheLists() {
        world.registered(server.addPlayer("Lone"), "hunter22");
        AccountInfo.View lone = world.accountInfo().of(new PlayerName("Lone")).orElseThrow();
        MenuContext empty = opening(lone);
        MenuContext full = opening(steve());

        assertThat(bindings.condition("uxmauth:no-logins").orElseThrow().test(empty, Map.of()))
                .isTrue();
        assertThat(bindings.condition("uxmauth:no-alts").orElseThrow().test(empty, Map.of()))
                .isTrue();
        assertThat(bindings.condition("uxmauth:no-logins").orElseThrow().test(full, Map.of()))
                .isFalse();
        assertThat(bindings.condition("uxmauth:no-alts").orElseThrow().test(full, Map.of()))
                .isFalse();
        assertThat(tooltip(item("last"), empty)).contains("When none");
        assertThat(tooltip(item("security"), empty)).contains("Mail address none");
    }

    @Test
    @DisplayName("a click on an alt reads that account, for the one who clicked")
    void anAltOpensItsOwn() {
        MenuContext context = opening(steve());
        Object alex = list("uxmauth:alts", context).getFirst();

        bindings.action("uxmauth:open-alt")
                .orElseThrow()
                .accept(new MenuActionContext(context.withEntry(alex), staff, ClickKind.LEFT, Map.of()));

        assertThat(opened).containsExactly("Staff -> Alex");
    }

    @Test
    @DisplayName("the window opens once its file is read, and a folder without the file leaves it to the lines")
    void theFileDecides() throws Exception {
        AccountInfo.View view = steve();
        assertThat(menu.show(staff, view, List.of())).as("nothing read yet").isFalse();

        Path menus = Files.createDirectories(folder.resolve("menus"));
        Files.writeString(menus.resolve("info.conf"), "rows = 1\nitems {}\n");
        menu.read(folder, Log.NONE);
        assertThat(menu.show(staff, view, List.of())).isTrue();

        Files.delete(menus.resolve("info.conf"));
        menu.read(folder, Log.NONE);
        assertThat(menu.show(staff, view, List.of()))
                .as("an operator who deleted the file took the window away")
                .isFalse();
        assertThat(Optional.ofNullable(staff.nextComponentMessage())).isEmpty();
    }
}
