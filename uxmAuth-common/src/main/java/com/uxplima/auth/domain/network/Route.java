package com.uxplima.auth.domain.network;

import java.util.Objects;

/**
 * A server named for the addresses a player may type to join, such as {@code eu.*}.
 *
 * @param host the address pattern, where a star stands for one part of a name
 */
public record Route(String host, String server) {
    public Route {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(server, "server");
    }
}
