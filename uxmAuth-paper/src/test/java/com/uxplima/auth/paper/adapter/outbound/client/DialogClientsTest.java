package com.uxplima.auth.paper.adapter.outbound.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** Which clients can draw a dialog: every one this server speaks to natively, and through ViaVersion 1.21.6 on. */
final class DialogClientsTest {

    private static final UUID WHO = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a client older than 1.21.6 cannot draw a dialog")
    void anOldClientCannot() {
        assertThat(new DialogClients(id -> 770).test(WHO)).isFalse();
    }

    @Test
    @DisplayName("a 1.21.6 client and every newer one can")
    void aNewClientCan() {
        assertThat(new DialogClients(id -> 771).test(WHO)).isTrue();
        assertThat(new DialogClients(id -> 800).test(WHO)).isTrue();
    }

    @Test
    @DisplayName("a client ViaVersion does not know is taken to speak the server's own version")
    void anUnknownClientIsTheServers() {
        assertThat(new DialogClients(id -> DialogClients.UNKNOWN).test(WHO)).isTrue();
    }

    @Test
    @DisplayName("without ViaVersion every client speaks the server's version, and every one can")
    void withoutViaVersionEveryClientCan() {
        ServerMock server = MockBukkit.mock();

        assertThat(DialogClients.on(server, Logger.getLogger("uxmauth-test")).test(WHO))
                .isTrue();
    }
}
