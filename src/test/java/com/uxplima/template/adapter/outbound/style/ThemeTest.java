package com.uxplima.template.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

final class ThemeTest {

    @Test
    @DisplayName("the shipped palette answers every role")
    void shipsEveryRole() {
        Theme theme = Theme.defaults();
        assertThat(theme.hex("accent")).isEqualTo("#38b6ff");
        assertThat(theme.hex("value")).isEqualTo("#8fd9ff");
        assertThat(theme.hasColour("nonsense")).isFalse();
    }

    @Test
    @DisplayName("a file changes the colours it names and keeps the rest")
    void overridesOneColour() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("colours", "accent").set("#ff0000");
        Theme theme = Theme.from(node);
        assertThat(theme.hex("accent")).isEqualTo("#ff0000");
        assertThat(theme.hex("body")).isEqualTo("#ffffff");
    }

    @Test
    @DisplayName("a category takes the colour the file gives it")
    void readsCategoryColours() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("prefix", "categories", "parkour").set("event");
        assertThat(Theme.from(node).categoryRole("PARKOUR")).isEqualTo("event");
        assertThat(Theme.from(node).categoryRole("tags")).isEqualTo("accent");
    }

    @Test
    @DisplayName("small capitals follow the language, so Turkish keeps its own letters")
    void readsSmallCapsPerLanguage() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("small-caps", "en").set(true);
        node.node("small-caps", "tr").set(false);
        Theme theme = Theme.from(node);
        assertThat(theme.smallCaps(Locale.ENGLISH)).isTrue();
        assertThat(theme.smallCaps(Locale.of("tr"))).isFalse();
    }

    @Test
    @DisplayName("an invalid colour is a defect at load, not a black message in the game")
    void refusesAnInvalidColour() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("colours", "accent").set("blue");
        assertThatThrownBy(() -> Theme.from(node)).isInstanceOf(IllegalArgumentException.class);
    }
}
