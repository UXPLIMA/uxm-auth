package com.uxplima.auth.paper.adapter.inbound.staff;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.paper.testing.MainThreadScheduler;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A removed or switched account's player is sent away in the words of whoever acted: staff, or the player. */
final class RemovedAccountsTest {

    private final MainThreadScheduler scheduler = new MainThreadScheduler();
    private final Messages messages = new Messages(
            new MessageCatalog(Map.of(Locale.ENGLISH, MessageCatalog.defaults(Keys.class)), Locale.ENGLISH),
            new LocaleSource() {
                @Override
                public Locale defaultLocale() {
                    return Locale.ENGLISH;
                }

                @Override
                public Locale localeOf(net.kyori.adventure.audience.Audience viewer) {
                    return Locale.ENGLISH;
                }
            });

    /** Sends the bulletin about a fresh player's account, and checks they were kicked with this reason. */
    private void kickedFor(boolean removed, Bulletin.By by, Keys reason) {
        Player steve = mock(Player.class);
        UUID id = UUID.randomUUID();
        Component expected = messages.render(steve, reason);
        new RemovedAccounts(account -> account.equals(id) ? steve : null, scheduler, messages)
                .accept(removed ? new Bulletin.Unregistered(id, by) : new Bulletin.Rejoin(id, by));
        scheduler.drain();
        verify(steve).kick(expected);
    }

    @Test
    @DisplayName("staff's removal and switch read as staff's, the player's own as their own")
    void theReasonNamesWhoActed() {
        kickedFor(true, Bulletin.By.STAFF, Keys.STAFF_REMOVED_KICK);
        kickedFor(false, Bulletin.By.STAFF, Keys.STAFF_REJOIN_KICK);
        kickedFor(true, Bulletin.By.OWNER, Keys.ACCOUNT_DELETED_KICK);
        kickedFor(false, Bulletin.By.OWNER, Keys.ACCOUNT_CRACKED_KICK);
    }
}
