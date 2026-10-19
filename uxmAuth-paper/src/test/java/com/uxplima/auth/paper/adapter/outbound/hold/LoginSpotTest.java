package com.uxplima.auth.paper.adapter.outbound.hold;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** The login spot is found in the world the settings name, and a world the server lacks is said once. */
final class LoginSpotTest {

    private ServerMock server;
    private final List<String> warned = new ArrayList<>();
    private final Logger logger = Logger.getLogger("uxmauth-login-spot-test");
    private Optional<AuthSettings.Place> spot = Optional.empty();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warned.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private LoginSpot spots() {
        return new LoginSpot(server, () -> new AuthSettings.Spots(spot, true), logger);
    }

    @Test
    @DisplayName("no spot set is no spot, and a set one is found in its world, facing the way it says")
    void aSpotIsFoundInItsWorld() {
        assertThat(spots().where()).isEmpty();

        World lobby = server.addSimpleWorld("lobby");
        spot = Optional.of(new AuthSettings.Place("lobby", 10.5, 70, -3.5, 90, 10));

        assertThat(spots().where()).contains(new Location(lobby, 10.5, 70, -3.5, 90, 10));
        assertThat(warned).isEmpty();
    }

    @Test
    @DisplayName("a world the server has not loaded keeps the player where they are, and the console hears it once")
    void aMissingWorldIsSaidOnce() {
        spot = Optional.of(new AuthSettings.Place("gone", 0, 64, 0, 0, 0));
        LoginSpot spots = spots();

        assertThat(spots.where()).isEmpty();
        assertThat(spots.where()).isEmpty();

        assertThat(warned)
                .singleElement()
                .asString()
                .contains("location.login-spot")
                .contains("gone");
    }
}
