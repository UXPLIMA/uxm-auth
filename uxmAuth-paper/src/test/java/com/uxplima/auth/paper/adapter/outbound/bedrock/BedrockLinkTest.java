package com.uxplima.auth.paper.adapter.outbound.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Floodgate as an optional link, and the prefix that keeps a Bedrock name apart from a Java one. */
final class BedrockLinkTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("without Floodgate nobody is a Bedrock player and there is no prefix")
    void withoutFloodgateNobodyIsBedrock() {
        BedrockLink link = BedrockLink.on(MockBukkit.mock(), Logger.getLogger("uxmauth-test"));

        assertThat(link.player(java.util.UUID.randomUUID())).isEmpty();
        assertThat(link.prefix()).isEmpty();
    }

    @Test
    @DisplayName("a Bedrock client is never shown a Java dialog, whatever its version")
    void aBedrockClientGetsNoDialog() {
        java.util.UUID bedrock = java.util.UUID.randomUUID();
        BedrockLink link = new BedrockLink() {
            @Override
            public java.util.Optional<BedrockLink.Player> player(java.util.UUID id) {
                return id.equals(bedrock)
                        ? java.util.Optional.of(new BedrockLink.Player(false))
                        : java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<String> prefix() {
                return java.util.Optional.of(".");
            }
        };

        java.util.function.Predicate<java.util.UUID> dialogs = BedrockLink.javaOnly(link, id -> true);

        assertThat(dialogs.test(bedrock)).isFalse();
        assertThat(dialogs.test(java.util.UUID.randomUUID())).isTrue();
    }

    @Test
    @DisplayName("an empty prefix, or one a Java name could start with, can let a Bedrock name collide")
    void aPrefixAJavaNameCanStartWithCollides() {
        assertThat(BedrockLink.collides("")).isTrue();
        assertThat(BedrockLink.collides("b")).isTrue();
        assertThat(BedrockLink.collides("_")).isTrue();
        assertThat(BedrockLink.collides("7")).isTrue();
        assertThat(BedrockLink.collides(".")).isFalse();
        assertThat(BedrockLink.collides("*")).isFalse();
    }
}
