package com.uxplima.auth.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Two listeners of what happened hear the same, and one that only cares for bans hears only those. */
final class AuthNoticesTest {

    private final List<String> heard = new ArrayList<>();

    @Test
    @DisplayName("both hears everything twice, in order, and the ban listener only bans")
    void bothAndBans() {
        AuthNotices bans = AuthNotices.onBan((address, length) -> heard.add("ban " + address + " " + length));
        AuthNotices both =
                AuthNotices.both(bans, AuthNotices.onBan((address, length) -> heard.add("again " + address)));

        both.registered(new PlayerName("Steve"), "203.0.113.7");
        both.loggedIn(new PlayerName("Steve"), "203.0.113.7", AuthMethod.PASSWORD);
        both.knownAddress(new PlayerName("Steve"), "203.0.113.7", 3);
        both.conflictSettled("Steve");
        both.banned("203.0.113.7", Duration.ofMinutes(5));

        assertThat(heard).containsExactly("ban 203.0.113.7 PT5M", "again 203.0.113.7");
    }

    @Test
    @DisplayName("both tells each of the two every kind of notice")
    void bothTellsEach() {
        AuthNotices first = recording("1");
        AuthNotices second = recording("2");
        AuthNotices both = AuthNotices.both(first, second);

        both.registered(new PlayerName("Steve"), "a");
        both.loggedIn(new PlayerName("Steve"), "a", AuthMethod.SESSION);
        both.knownAddress(new PlayerName("Steve"), "a", 2);
        both.banned("a", Duration.ofSeconds(1));
        both.conflictSettled("Steve");

        assertThat(heard)
                .containsExactly(
                        "1 registered",
                        "2 registered",
                        "1 login",
                        "2 login",
                        "1 known",
                        "2 known",
                        "1 ban",
                        "2 ban",
                        "1 settled",
                        "2 settled");
    }

    private AuthNotices recording(String who) {
        return new AuthNotices() {
            @Override
            public void registered(PlayerName name, String address) {
                heard.add(who + " registered");
            }

            @Override
            public void loggedIn(PlayerName name, String address, AuthMethod method) {
                heard.add(who + " login");
            }

            @Override
            public void knownAddress(PlayerName name, String address, int accounts) {
                heard.add(who + " known");
            }

            @Override
            public void banned(String address, Duration length) {
                heard.add(who + " ban");
            }

            @Override
            public void conflictSettled(String name) {
                heard.add(who + " settled");
            }
        };
    }
}
