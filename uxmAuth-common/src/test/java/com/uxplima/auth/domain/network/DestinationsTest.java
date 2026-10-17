package com.uxplima.auth.domain.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Where a player goes on a network: an auth server while waiting, a lobby or their last server after login. */
final class DestinationsTest {

    private static final List<String> LOBBIES = List.of("lobby-1", "lobby-2", "lobby-3");

    private final Map<String, Integer> online = new LinkedHashMap<>(Map.of("lobby-1", 9, "lobby-2", 2, "lobby-3", 5));

    private static Destinations rule(Balance balance, AfterLogin after, List<Route> routes) {
        return new Destinations(balance, after, LOBBIES, routes, RandomGenerator.of("L64X128MixRandom"));
    }

    @Test
    @DisplayName(
            "the balance picks the emptiest server, the first one listed, or one at random, among those that are up")
    void theBalancePicks() {
        assertThat(rule(Balance.LEAST_PLAYERS, AfterLogin.LOBBY, List.of()).pick(LOBBIES, online))
                .contains("lobby-2");
        assertThat(rule(Balance.FIRST, AfterLogin.LOBBY, List.of()).pick(LOBBIES, online))
                .contains("lobby-1");
        assertThat(rule(Balance.RANDOM, AfterLogin.LOBBY, List.of()).pick(LOBBIES, online))
                .isPresent();

        online.remove("lobby-1");
        assertThat(rule(Balance.FIRST, AfterLogin.LOBBY, List.of()).pick(LOBBIES, online))
                .contains("lobby-2");
        assertThat(rule(Balance.FIRST, AfterLogin.LOBBY, List.of()).pick(List.of("gone"), online))
                .isEmpty();
    }

    @Test
    @DisplayName(
            "after login a player goes to a lobby, to their last server, or to the server of the address they typed")
    void afterLoginFollowsTheRule() {
        assertThat(rule(Balance.FIRST, AfterLogin.LOBBY, List.of())
                        .afterLogin("play.example.org", Optional.of("lobby-3"), online))
                .contains("lobby-1");
        assertThat(rule(Balance.FIRST, AfterLogin.LAST_SERVER, List.of())
                        .afterLogin("play.example.org", Optional.of("lobby-3"), online))
                .contains("lobby-3");
        assertThat(rule(
                                Balance.FIRST,
                                AfterLogin.BY_DOMAIN,
                                List.of(new Route("eu.example.org", "lobby-2"), new Route("*.example.org", "lobby-3")))
                        .afterLogin("eu.example.org", Optional.empty(), online))
                .contains("lobby-2");
    }

    @Test
    @DisplayName("a last server that is gone, or an address no route names, falls back to the lobbies")
    void anUnknownTargetFallsBackToTheLobbies() {
        assertThat(rule(Balance.FIRST, AfterLogin.LAST_SERVER, List.of())
                        .afterLogin("play.example.org", Optional.of("survival"), online))
                .contains("lobby-1");
        assertThat(rule(Balance.FIRST, AfterLogin.LAST_SERVER, List.of())
                        .afterLogin("play.example.org", Optional.empty(), online))
                .contains("lobby-1");
        assertThat(rule(Balance.FIRST, AfterLogin.BY_DOMAIN, List.of(new Route("eu.*", "lobby-2")))
                        .afterLogin("us.example.org", Optional.empty(), online))
                .contains("lobby-1");
    }
}
