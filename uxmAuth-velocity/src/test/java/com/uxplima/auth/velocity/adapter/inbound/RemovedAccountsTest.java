package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.kyori.adventure.text.Component;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.velocity.adapter.outbound.ProxyLocales;
import com.uxplima.uxmlib.text.language.LanguageSettings;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;
import com.velocitypowered.api.proxy.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Staff removed an account: its player leaves the network, and the proxy forgets their login. */
final class RemovedAccountsTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    private final Messages messages = new Messages(
            new MessageCatalog(Map.of(Locale.ENGLISH, MessageCatalog.defaults(Keys.class)), Locale.ENGLISH),
            new ProxyLocales(() -> LanguageSettings.following(Locale.ENGLISH)));
    private final ProxySessions sessions = new ProxySessions();

    @Test
    @DisplayName("the account's player is sent away with the reason and is no longer logged in on the proxy")
    void thePlayerLeaves() {
        Player steve = mock(Player.class);
        sessions.waiting(STEVE, new Claim.Waiting(false, false, false));
        sessions.loggedIn(STEVE, com.uxplima.auth.domain.state.AuthMethod.PASSWORD);
        RemovedAccounts removed =
                new RemovedAccounts(id -> id.equals(STEVE) ? Optional.of(steve) : Optional.empty(), sessions, messages);

        removed.accept(new Bulletin.Unregistered(STEVE));

        verify(steve).disconnect(messages.render(steve, Keys.STAFF_REMOVED_KICK));
        assertThat(sessions.loggedIn(STEVE)).isFalse();
    }

    @Test
    @DisplayName("any other bulletin sends nobody away")
    void otherBulletinsSendNobody() {
        Player steve = mock(Player.class);
        RemovedAccounts removed = new RemovedAccounts(id -> Optional.of(steve), sessions, messages);

        removed.accept(new Bulletin.SessionsEnded(STEVE, Optional.empty()));

        verify(steve, never()).disconnect(any(Component.class));
    }
}
