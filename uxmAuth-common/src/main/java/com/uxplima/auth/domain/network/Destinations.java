package com.uxplima.auth.domain.network;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

import com.uxplima.auth.domain.premium.HostPatterns;

/**
 * Which server a player is sent to on a network: an auth server while waiting, and once logged in a lobby, the
 * last server or the server of the address they typed, as the operator chose. A server that is not up is never
 * picked, and every rule falls back to the lobbies.
 */
public final class Destinations {

    private final Balance balance;
    private final AfterLogin after;
    private final List<String> lobbies;
    private final List<Route> routes;
    private final RandomGenerator random;

    public Destinations(
            Balance balance, AfterLogin after, List<String> lobbies, List<Route> routes, RandomGenerator random) {
        this.balance = Objects.requireNonNull(balance, "balance");
        this.after = Objects.requireNonNull(after, "after");
        this.lobbies = List.copyOf(Objects.requireNonNull(lobbies, "lobbies"));
        this.routes = List.copyOf(Objects.requireNonNull(routes, "routes"));
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * One of these servers by the balance.
     *
     * @param online the players on each server that is up, by name; a server not in it is down or unknown
     */
    public Optional<String> pick(List<String> names, Map<String, Integer> online) {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(online, "online");
        List<String> up = names.stream().filter(online::containsKey).toList();
        if (up.isEmpty()) {
            return Optional.empty();
        }
        return switch (balance) {
            case FIRST -> Optional.of(up.getFirst());
            case RANDOM -> Optional.of(up.get(random.nextInt(up.size())));
            case LEAST_PLAYERS -> up.stream().min(Comparator.comparingInt(online::get));
        };
    }

    /** Where a player who just logged in goes, from the address they typed and the server they last left. */
    public Optional<String> afterLogin(String host, Optional<String> lastServer, Map<String, Integer> online) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(lastServer, "lastServer");
        Objects.requireNonNull(online, "online");
        Optional<String> chosen =
                switch (after) {
                    case LOBBY -> Optional.empty();
                    case LAST_SERVER -> lastServer.filter(online::containsKey);
                    case BY_DOMAIN ->
                        routes.stream()
                                .filter(route -> new HostPatterns(List.of(route.host())).matches(host))
                                .map(Route::server)
                                .filter(online::containsKey)
                                .findFirst();
                };
        return chosen.or(() -> pick(lobbies, online));
    }
}
