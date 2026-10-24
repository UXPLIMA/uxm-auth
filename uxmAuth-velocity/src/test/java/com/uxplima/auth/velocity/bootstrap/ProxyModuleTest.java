package com.uxplima.auth.velocity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.auth.adapter.outbound.AuthSchema;
import com.uxplima.auth.adapter.outbound.RecordedScheme;
import com.uxplima.auth.adapter.outbound.SchemeChanged;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.velocity.adapter.inbound.ClaimWriter;
import com.uxplima.auth.velocity.adapter.inbound.DoorListener;
import com.uxplima.auth.velocity.adapter.inbound.HoldListener;
import com.uxplima.auth.velocity.adapter.inbound.ReportListener;
import com.uxplima.auth.velocity.adapter.inbound.RouteListener;
import com.uxplima.auth.velocity.adapter.outbound.limbo.LimboWait;
import com.uxplima.uxmlib.storage.sql.Database;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.EventHandler;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.config.ProxyConfig;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.world.VirtualWorld;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.slf4j.Logger;

/** The proxy half starts the way a server does: its files, its database and the scheme it was first run under. */
final class ProxyModuleTest {

    private final Object plugin = new Object();
    private final ProxyServer proxy = mock(ProxyServer.class);
    private final EventManager events = mock(EventManager.class);
    private final Logger logger = mock(Logger.class);

    private final ProxyConfig config = mock(ProxyConfig.class);
    private final ChannelRegistrar channels = mock(ChannelRegistrar.class);
    private final com.velocitypowered.api.scheduler.Scheduler scheduler =
            mock(com.velocitypowered.api.scheduler.Scheduler.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);

    ProxyModuleTest() {
        when(proxy.getEventManager()).thenReturn(events);
        when(proxy.getConfiguration()).thenReturn(config);
        when(proxy.getChannelRegistrar()).thenReturn(channels);
        when(proxy.getCommandManager()).thenReturn(mock(CommandManager.class));
        when(proxy.getScheduler()).thenReturn(scheduler);
    }

    @Test
    @DisplayName("an offline proxy puts the door before every connection and says premium auto-login is on")
    void anOfflineProxyRunsTheDoor(@TempDir Path folder) {
        ProxyModule module = new ProxyModule(plugin, proxy, logger, folder);

        module.wire();
        module.shutdown();

        verify(events).register(eq(plugin), any(DoorListener.class));
        verify(events).register(eq(plugin), any(ClaimWriter.class));
        verify(events).register(eq(plugin), any(ReportListener.class));
        verify(events).register(eq(plugin), any(HoldListener.class));
        verify(events).register(eq(plugin), any(RouteListener.class));
        verify(channels).register(ReportListener.CHANNEL);
        verify(logger).info(contains("Premium auto-login is on"));
        // The country file is looked after off the proxy's threads, from the start and every few hours.
        verify(scheduler).buildTask(eq(plugin), any(com.uxplima.auth.adapter.outbound.geo.CountryUpkeep.class));
    }

    @Test
    @DisplayName("under opt-in the proxy says only a player who typed /premium is proved")
    void anOptInProxySaysSo(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve("config.conf"), "ownership { rule = opt-in }\n", StandardCharsets.UTF_8);
        ProxyModule module = new ProxyModule(plugin, proxy, logger, folder);

        module.wire();
        module.shutdown();

        verify(logger).info(contains("only a player who typed /premium"));
    }

    @Test
    @DisplayName("the first start makes the network secret, writes it beside the config and says where")
    void theFirstStartMakesTheSecret(@TempDir Path folder) throws IOException {
        ProxyModule module = new ProxyModule(plugin, proxy, logger, folder);

        module.wire();
        module.shutdown();

        assertThat(Files.readString(folder.resolve("network-secret.txt"), StandardCharsets.UTF_8)
                        .trim())
                .hasSize(43);
        verify(logger).info(contains("network secret"), any(Object.class));
    }

    @Test
    @DisplayName("an online-mode proxy proves every player itself, so the door stays out and the console says why")
    void anOnlineProxyRunsNoDoor(@TempDir Path folder) {
        when(config.isOnlineMode()).thenReturn(true);
        ProxyModule module = new ProxyModule(plugin, proxy, logger, folder);

        module.wire();
        module.shutdown();

        verify(events, never()).register(eq(plugin), any(DoorListener.class));
        verify(logger).warn(contains("online-mode"));
    }

    @Test
    @DisplayName("the first start writes the files, opens the database and records the UUID scheme")
    void theFirstStartRecordsTheScheme(@TempDir Path folder) {
        ProxyModule module = new ProxyModule(plugin, proxy, logger, folder);

        assertThat(module.wire()).isTrue();
        module.shutdown();

        assertThat(folder.resolve("config.conf")).isRegularFile();
        assertThat(folder.resolve("messages").resolve("messages_en.conf")).isRegularFile();
        assertThat(folder.resolve("mail").resolve("verify_tr.txt")).isRegularFile();
        try (Database database = AuthSchema.migrate(Database.builder()
                .sqlite(folder.resolve("auth.db"))
                .poolName("check")
                .build())) {
            RecordedScheme recorded = new RecordedScheme(database);
            recorded.check(UuidScheme.KEEP_FIRST);
            assertThatThrownBy(() -> recorded.check(UuidScheme.OFFLINE)).isInstanceOf(SchemeChanged.class);
        }
        verify(events)
                .register(
                        any(), eq(ProxyShutdownEvent.class), ArgumentMatchers.<EventHandler<ProxyShutdownEvent>>any());
    }

    @Test
    @DisplayName("a start under another UUID scheme says why in one line and does nothing else")
    void anotherSchemeStopsTheStart(@TempDir Path folder) throws IOException {
        ProxyModule first = new ProxyModule(plugin, proxy, logger, folder);
        first.wire();
        first.shutdown();
        Path config = folder.resolve("config.conf");
        Files.writeString(
                config,
                Files.readString(config, StandardCharsets.UTF_8)
                        .replace("uuid-scheme = keep-first", "uuid-scheme = offline"),
                StandardCharsets.UTF_8);
        EventManager later = mock(EventManager.class);
        when(proxy.getEventManager()).thenReturn(later);

        assertThat(new ProxyModule(plugin, proxy, logger, folder).wire()).isFalse();

        verify(logger).error(contains("ownership.uuid-scheme"));
        verifyNoInteractions(later);
        verify(logger, never()).error(anyString(), any(Throwable.class));
    }

    @Test
    @DisplayName("a proxy on a file database is told that the servers behind it must read the same one")
    void aFileDatabaseIsWarnedOf(@TempDir Path folder) {
        ProxyModule module = new ProxyModule(plugin, proxy, logger, folder);

        module.wire();
        module.shutdown();

        verify(logger).warn(contains("storage.jdbc-url"));
    }

    @Test
    @DisplayName("the messages are read from the shipped files")
    void theMessagesAreRead(@TempDir Path folder) {
        ProxyModule module = new ProxyModule(plugin, proxy, logger, folder);
        module.wire();

        assertThat(module.messages()).isNotNull();
        module.shutdown();
    }

    @Test
    @DisplayName("the proxy closes its database when it shuts down")
    void theShutdownClosesTheDatabase(@TempDir Path folder) {
        ProxyModule module = new ProxyModule(plugin, proxy, logger, folder);
        module.wire();
        Database open = module.database();
        assertThat(open).isNotNull();

        module.onShutdown(new ProxyShutdownEvent());

        assertThat(Objects.requireNonNull(open).ping()).isFalse();
        assertThat(module.database()).isNull();
    }

    /** A proxy whose config.conf asks for the waiting world. */
    private ProxyModule askingForTheWorld(Path folder) throws IOException {
        ProxyModule first = new ProxyModule(plugin, proxy, logger, folder);
        first.wire();
        first.shutdown();
        Path config = folder.resolve("config.conf");
        Files.writeString(
                config,
                Files.readString(config, StandardCharsets.UTF_8).replace("wait-in = server", "wait-in = limbo"),
                StandardCharsets.UTF_8);
        return new ProxyModule(plugin, proxy, logger, folder);
    }

    @Test
    @DisplayName("a proxy asked for the waiting world without LimboAPI does not start, and says why")
    void withoutLimboApiTheStartStops(@TempDir Path folder) throws IOException {
        PluginManager plugins = mock(PluginManager.class);
        when(proxy.getPluginManager()).thenReturn(plugins);
        when(plugins.getPlugin("limboapi")).thenReturn(Optional.empty());
        ProxyModule module = askingForTheWorld(folder);

        // Waiting on a server instead would let a player into a server that runs no uxmAuth of its own.
        assertThat(module.wire()).isFalse();

        verify(logger).error(contains("LimboAPI is not on this proxy"));
        verify(events, never()).register(eq(plugin), any(LimboWait.class));
        assertThat(module.database()).isNull();
    }

    @Test
    @DisplayName("a proxy asked for the waiting world with LimboAPI builds it and holds the waiting players there")
    void withLimboApiThePlayersWaitInTheWorld(@TempDir Path folder) throws IOException {
        LimboFactory factory = mock(LimboFactory.class);
        Limbo limbo = mock(Limbo.class, RETURNS_SELF);
        when(factory.createLimbo(any())).thenReturn(limbo);
        when(factory.createVirtualWorld(any(), anyDouble(), anyDouble(), anyDouble(), anyFloat(), anyFloat()))
                .thenReturn(mock(VirtualWorld.class));
        PluginContainer container = mock(PluginContainer.class);
        doReturn(Optional.of(factory)).when(container).getInstance();
        PluginManager plugins = mock(PluginManager.class);
        when(proxy.getPluginManager()).thenReturn(plugins);
        when(plugins.getPlugin("limboapi")).thenReturn(Optional.of(container));
        CommandManager commands = mock(CommandManager.class);
        when(proxy.getCommandManager()).thenReturn(commands);
        when(commands.metaBuilder(anyString())).thenReturn(mock(CommandMeta.Builder.class, RETURNS_SELF));
        ProxyModule module = askingForTheWorld(folder);

        module.wire();
        module.shutdown();

        verify(events).register(eq(plugin), any(LimboWait.class));
        // /login, /register, /changepassword for a weak password the login held up, /captcha, /2fa, and /recover.
        verify(limbo, org.mockito.Mockito.times(6)).registerCommand(org.mockito.ArgumentMatchers.<CommandMeta>any());
        verify(commands).metaBuilder("changepassword");
        verify(commands).metaBuilder("captcha");
        verify(commands).metaBuilder("2fa");
        verify(commands).metaBuilder("recover");
        verify(logger).info(contains("proxy's own world"));
    }

    @Test
    @DisplayName("a LimboAPI this plugin cannot speak to stops the start, and says which releases it speaks to")
    void aForeignLimboApiStopsTheStart(@TempDir Path folder) throws IOException {
        PluginContainer container = mock(PluginContainer.class);
        doReturn(Optional.of(new Object())).when(container).getInstance();
        PluginManager plugins = mock(PluginManager.class);
        when(proxy.getPluginManager()).thenReturn(plugins);
        when(plugins.getPlugin("limboapi")).thenReturn(Optional.of(container));
        CommandManager commands = mock(CommandManager.class);
        when(proxy.getCommandManager()).thenReturn(commands);
        when(commands.metaBuilder(anyString())).thenReturn(mock(CommandMeta.Builder.class, RETURNS_SELF));
        ProxyModule module = askingForTheWorld(folder);

        assertThat(module.wire()).isFalse();

        verify(events, never()).register(eq(plugin), any(LimboWait.class));
        verify(logger).error(contains("uxmAuth speaks to LimboAPI 1.1 and LimboAPI 2"));
        assertThat(module.database()).isNull();
    }

    @Test
    @DisplayName("a proxy that keeps players on a server never asks for LimboAPI")
    void aServerWaitNeverAsksForLimboApi(@TempDir Path folder) {
        ProxyModule module = new ProxyModule(plugin, proxy, logger, folder);

        module.wire();
        module.shutdown();

        verify(proxy, never()).getPluginManager();
    }
}
