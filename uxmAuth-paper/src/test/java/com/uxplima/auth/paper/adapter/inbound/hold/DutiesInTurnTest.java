package com.uxplima.auth.paper.adapter.inbound.hold;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Two duties are owed one after the other: the second is asked only once the first is done. */
final class DutiesInTurnTest {

    private final List<String> done = new ArrayList<>();
    private PlayerMock steve;
    private AuthState.Authenticated in;

    @BeforeEach
    void setUp() {
        steve = MockBukkit.mock().addPlayer("Steve");
        in = new AuthState.Authenticated(
                new Account(
                        UUID.randomUUID(),
                        new PlayerName("Steve"),
                        AccountKind.PREMIUM,
                        null,
                        null,
                        java.time.Instant.EPOCH,
                        "127.0.0.1",
                        null,
                        null),
                AuthMethod.PREMIUM);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A duty owed or not, which when held is done at once and says so. */
    private Door.Duty duty(String name, boolean owes) {
        return new Door.Duty() {
            @Override
            public boolean owes(Player player) {
                return owes;
            }

            @Override
            public void hold(Player player, AuthState.Authenticated held, Runnable proceed) {
                done.add(name);
                proceed.run();
            }
        };
    }

    private void hold(Door.Duty duty) {
        if (duty.owes(steve)) {
            duty.hold(steve, in, () -> done.add("in"));
        } else {
            done.add("free");
        }
    }

    @Test
    @DisplayName("both owed: the first, then the second, then in")
    void bothInOrder() {
        hold(Door.Duty.inTurn(duty("app", true), duty("address", true)));

        assertThat(done).containsExactly("app", "address", "in");
    }

    @Test
    @DisplayName("only one owed: that one, then in; neither owed: nothing to hold")
    void onlyWhatIsOwed() {
        hold(Door.Duty.inTurn(duty("app", false), duty("address", true)));
        hold(Door.Duty.inTurn(duty("app", true), duty("address", false)));
        hold(Door.Duty.inTurn(duty("app", false), duty("address", false)));

        assertThat(done).containsExactly("address", "in", "app", "in", "free");
    }
}
