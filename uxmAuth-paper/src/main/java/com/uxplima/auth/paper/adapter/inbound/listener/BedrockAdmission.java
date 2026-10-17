package com.uxplima.auth.paper.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.uxplima.auth.application.LetBedrockIn;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.outbound.bedrock.BedrockLink;
import org.jspecify.annotations.Nullable;

/** The door for a Bedrock player Floodgate vouches for, while config.conf lets them in without a password. */
public final class BedrockAdmission {

    /** No Bedrock door: every connection is asked as usual. */
    public static final BedrockAdmission NONE = new BedrockAdmission();

    private final BedrockLink link;
    private final @Nullable LetBedrockIn letIn;
    private final BooleanSupplier autoLogin;

    private BedrockAdmission() {
        this.link = BedrockLink.NONE;
        this.letIn = null;
        this.autoLogin = () -> false;
    }

    public BedrockAdmission(BedrockLink link, LetBedrockIn letIn, BooleanSupplier autoLogin) {
        this.link = Objects.requireNonNull(link, "link");
        this.letIn = Objects.requireNonNull(letIn, "letIn");
        this.autoLogin = Objects.requireNonNull(autoLogin, "autoLogin");
    }

    /** The state a Bedrock connection opens with, or empty when the door asks as it does for anyone. */
    public Optional<AuthState> admit(UUID connection, PlayerName name, String address) {
        LetBedrockIn door = letIn;
        if (door == null || !autoLogin.getAsBoolean()) {
            return Optional.empty();
        }
        return link.player(connection).map(player -> door.admit(connection, name, player.linked(), address));
    }
}
