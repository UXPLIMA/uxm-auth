package com.uxplima.auth.paper.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.application.LetBedrockIn;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.outbound.bedrock.BedrockLink;
import com.uxplima.auth.paper.testing.AuthWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Who Floodgate vouches for comes in at the door while the file lets them, and nobody else does. */
final class BedrockAdmissionTest {

    @TempDir
    Path folder;

    private AuthWorld world;
    private boolean autoLogin = true;
    private final UUID bedrock = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a Bedrock player comes in at the door while auto-login is on")
    void aBedrockPlayerComesIn() {
        assertThat(admission().admit(bedrock, new PlayerName(".Steve"), "10.0.0.7"))
                .containsInstanceOf(AuthState.Authenticated.class);
    }

    @Test
    @DisplayName("with auto-login off, or for a Java player, the door asks as usual")
    void otherwiseTheDoorAsks() {
        assertThat(admission().admit(UUID.randomUUID(), new PlayerName("Steve"), "10.0.0.7"))
                .isEmpty();

        autoLogin = false;
        assertThat(admission().admit(bedrock, new PlayerName(".Steve"), "10.0.0.7"))
                .isEmpty();
    }

    private BedrockAdmission admission() {
        BedrockLink link = new BedrockLink() {
            @Override
            public Optional<BedrockLink.Player> player(UUID id) {
                return id.equals(bedrock) ? Optional.of(new BedrockLink.Player(false)) : Optional.empty();
            }

            @Override
            public Optional<String> prefix() {
                return Optional.of(".");
            }
        };
        return new BedrockAdmission(
                link, new LetBedrockIn(world.accounts, () -> AuthWorld.MAX_ATTEMPTS, world.clock), () -> autoLogin);
    }
}
