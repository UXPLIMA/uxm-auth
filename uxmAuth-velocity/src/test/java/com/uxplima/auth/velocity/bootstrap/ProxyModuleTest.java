package com.uxplima.auth.velocity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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

import com.uxplima.auth.adapter.outbound.AuthSchema;
import com.uxplima.auth.adapter.outbound.RecordedScheme;
import com.uxplima.auth.adapter.outbound.SchemeChanged;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.velocity.adapter.inbound.ClaimWriter;
import com.uxplima.auth.velocity.adapter.inbound.DoorListener;
import com.uxplima.auth.velocity.adapter.inbound.ReportListener;
import com.uxplima.uxmlib.storage.sql.Database;
import com.velocitypowered.api.event.EventHandler;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.config.ProxyConfig;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
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

    ProxyModuleTest() {
        when(proxy.getEventManager()).thenReturn(events);
        when(proxy.getConfiguration()).thenReturn(config);
        when(proxy.getChannelRegistrar()).thenReturn(channels);
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
        verify(channels).register(ReportListener.CHANNEL);
        verify(logger).info(contains("Premium auto-login is on"));
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
}
