package com.uxplima.auth.application.port;

import java.time.Duration;

import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;

/**
 * Where what happened is told outside the game, a Discord channel or nothing. Each is called on whatever thread the
 * event happened on, a server thread among them, so an implementation hands the work off and returns at once.
 * An address is handed whole; what leaves the server is the implementation's to mask.
 */
public interface AuthNotices {

    /** Tells nobody. */
    AuthNotices NONE = new AuthNotices() {
        @Override
        public void registered(PlayerName name, String address) {}

        @Override
        public void loggedIn(PlayerName name, String address, AuthMethod method) {}

        @Override
        public void knownAddress(PlayerName name, String address, int accounts) {}

        @Override
        public void banned(String address, Duration length) {}

        @Override
        public void conflictSettled(String name) {}
    };

    void registered(PlayerName name, String address);

    void loggedIn(PlayerName name, String address, AuthMethod method);

    /** A new account logged in from an address this many accounts, itself among them, have used. */
    void knownAddress(PlayerName name, String address, int accounts);

    void banned(String address, Duration length);

    /** Staff settled the name a cracked holder and a paid owner both wanted. */
    void conflictSettled(String name);
}
