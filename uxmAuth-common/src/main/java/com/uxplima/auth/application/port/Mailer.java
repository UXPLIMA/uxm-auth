package com.uxplima.auth.application.port;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.uxplima.auth.domain.mail.EmailAddress;

/** Mail out, on the mailer's own threads, never a server's. */
public interface Mailer {

    /** The mails this plugin sends, each with a file per language. */
    enum Kind {
        /** The code that confirms an address belongs to the player. */
        VERIFY("verify"),
        /** The code of the second step, sent to a confirmed address. */
        LOGIN_CODE("login-code"),
        /** The code that lets a player who forgot the password set a new one. */
        RECOVERY("recovery");

        private final String file;

        Kind(String file) {
            this.file = file;
        }

        /** The name its files start with. */
        public String file() {
            return file;
        }
    }

    /** What became of a mail. */
    enum Outcome {
        /** The server took it. */
        SENT,
        /** The operator has not turned mail on. */
        OFF,
        /** It could not be sent; the console says why, without the address. */
        FAILED
    }

    /** Mail is off: nothing is ever sent. */
    Mailer NONE = (kind, to, language, values) -> {
        Objects.requireNonNull(kind, "kind");
        return CompletableFuture.completedFuture(Outcome.OFF);
    };

    /**
     * Sends this kind of mail to the address, in the language, with each value in place of its placeholder.
     *
     * @param values the placeholders by name, as {@code code} stands for {@code {code}}
     */
    CompletableFuture<Outcome> send(Kind kind, EmailAddress to, Locale language, Map<String, String> values);
}
