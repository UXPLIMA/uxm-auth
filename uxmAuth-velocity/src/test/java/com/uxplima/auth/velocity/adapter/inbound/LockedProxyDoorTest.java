package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.UUID;

import net.kyori.adventure.text.Component;

import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import org.junit.jupiter.api.Test;

class LockedProxyDoorTest {

    private static final Component WHY = Component.text("Giriş sistemi şu an kullanılamıyor, birazdan tekrar dene.");

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final LockedProxyDoor door = new LockedProxyDoor(WHY);

    @Test
    void refusesEveryPreLogin() {
        PreLoginEvent event = new PreLoginEvent(stub(InboundConnection.class), "Steve", STEVE);

        door.onPreLogin(event);

        assertThat(event.getResult().isAllowed()).isFalse();
        assertThat(event.getResult().getReasonComponent()).contains(WHY);
    }

    @Test
    void shutsAgainWhatAPluginBetweenOpened() {
        PreLoginEvent event = new PreLoginEvent(stub(InboundConnection.class), "Steve", STEVE);
        door.onPreLogin(event);

        // Floodgate does this for a Bedrock connection, whatever the result before it.
        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
        door.onPreLoginAgain(event);

        assertThat(event.getResult().isAllowed()).isFalse();
        assertThat(event.getResult().isForceOfflineMode()).isFalse();
    }

    @Test
    void refusesALoginSomePluginLetThrough() {
        LoginEvent event = new LoginEvent(stub(Player.class), "");

        door.onLogin(event);

        assertThat(event.getResult().isAllowed()).isFalse();
        assertThat(event.getResult().getReasonComponent()).contains(WHY);
    }

    @Test
    void speaksEnglishWithoutTheOperatorsWords() {
        assertThat(LockedProxyDoor.defaultReason()).isNotEqualTo(Component.empty());
    }

    /** A connection the door never reads: it only ever sets the result. */
    private static <T> T stub(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            throw new UnsupportedOperationException(method.getName());
        }));
    }
}
