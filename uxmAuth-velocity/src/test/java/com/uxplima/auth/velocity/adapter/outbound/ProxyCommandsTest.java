package com.uxplima.auth.velocity.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.domain.state.AuthMethod;
import com.velocitypowered.api.proxy.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The proxy runs the command lines of the login moments as its own commands, and nothing else of them. */
final class ProxyCommandsTest {

    private static final UUID STEVE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

    private final List<String> ran = new ArrayList<>();
    private final Player steve = mock(Player.class);

    private ProxyCommands commands(Map<AuthSettings.Moment, List<String>> lists) {
        when(steve.getUsername()).thenReturn("Steve");
        when(steve.getUniqueId()).thenReturn(STEVE);
        return new ProxyCommands(
                () -> new AuthSettings.Effects(lists),
                id -> id.equals(STEVE) ? Optional.of(steve) : Optional.empty(),
                new ProxyCommands.Runner() {
                    @Override
                    public void console(String command) {
                        ran.add("console: " + command);
                    }

                    @Override
                    public void player(Player player, String command) {
                        ran.add(player.getUsername() + ": " + command);
                    }
                });
    }

    @Test
    @DisplayName("a login runs the console and player lines of logged-in with the player's name and id in them")
    void aLoginRunsItsCommands() {
        Map<AuthSettings.Moment, List<String>> lists = new EnumMap<>(AuthSettings.Moment.class);
        lists.put(
                AuthSettings.Moment.LOGGED_IN,
                List.of(
                        "[title] @welcome.logged-in",
                        "[console] alert %player_name% is here (%player_uuid%)",
                        "[player] {delay=20} /server lobby",
                        "[sound] block.note_block.chime 0.6 1.2"));
        lists.put(AuthSettings.Moment.REGISTERED, List.of("[console] welcome %player_name%"));

        commands(lists).loggedIn(STEVE, AuthMethod.PASSWORD);

        assertThat(ran).containsExactly("console: alert Steve is here (" + STEVE + ")", "Steve: server lobby");
    }

    @Test
    @DisplayName("a registration runs the registered lines, and a player who is gone runs nothing")
    void aRegistrationRunsItsOwnList() {
        Map<AuthSettings.Moment, List<String>> lists = new EnumMap<>(AuthSettings.Moment.class);
        lists.put(AuthSettings.Moment.LOGGED_IN, List.of("[console] login %player_name%"));
        lists.put(AuthSettings.Moment.REGISTERED, List.of("[console] welcome %player_name%"));
        ProxyCommands commands = commands(lists);

        commands.loggedIn(STEVE, AuthMethod.REGISTERED);
        commands.loggedIn(UUID.randomUUID(), AuthMethod.PASSWORD);

        assertThat(ran).containsExactly("console: welcome Steve");
    }
}
