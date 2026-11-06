package com.uxplima.auth.velocity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.auth.velocity.adapter.inbound.LockedProxyDoor;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

class AuthVelocityPluginTest {

    @Test
    void aStartThatWentThroughIsStarted() {
        assertThat(AuthVelocityPlugin.started(() -> true, NOPLogger.NOP_LOGGER)).isTrue();
    }

    @Test
    void aStartTheModuleRefusedIsNotStarted() {
        assertThat(AuthVelocityPlugin.started(() -> false, NOPLogger.NOP_LOGGER))
                .isFalse();
    }

    @Test
    void anExceptionLocksTheDoor() {
        assertThat(AuthVelocityPlugin.started(
                        () -> {
                            throw new IllegalStateException("the database does not answer");
                        },
                        NOPLogger.NOP_LOGGER))
                .isFalse();
    }

    @Test
    void anErrorLocksTheDoorToo() {
        assertThat(AuthVelocityPlugin.started(
                        () -> {
                            throw new NoClassDefFoundError("com/example/Missing");
                        },
                        NOPLogger.NOP_LOGGER))
                .isFalse();
    }

    @Test
    void withoutTheOperatorsWordsTheDoorSpeaksEnglish() {
        assertThat(AuthVelocityPlugin.reason(null)).isEqualTo(LockedProxyDoor.defaultReason());
    }
}
