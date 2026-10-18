package com.uxplima.auth.paper.adapter.inbound.placeholder;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.OfflinePlayer;

import com.uxplima.auth.application.factor.FactorStates;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.uxmlib.hook.placeholder.PlaceholderProvider;
import org.jspecify.annotations.Nullable;

/**
 * What a scoreboard, a tab list or a chat format reads of a player. Every answer comes from the session in memory
 * and the second-factor states kept for the players here, never from the database, since PlaceholderAPI asks on
 * whatever thread it is on. A player who has not logged in reads {@code false} for {@code authenticated} and nothing
 * for the rest. The keys are listed above the {@code placeholders} block of {@code config.conf}.
 */
public final class AuthPlaceholders implements PlaceholderProvider {

    /** The namespace an operator writes: {@code %uxmauth_...%}. */
    public static final String IDENTIFIER = "uxmauth";

    private static final String NOTHING = "";

    private final Function<UUID, Optional<AuthState>> sessions;
    private final FactorStates factors;
    private final Supplier<DateTimeFormatter> dates;

    /**
     * @param sessions the state of the player's session, read from memory
     * @param dates how a date reads, with its time zone
     */
    public AuthPlaceholders(
            Function<UUID, Optional<AuthState>> sessions, FactorStates factors, Supplier<DateTimeFormatter> dates) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.factors = Objects.requireNonNull(factors, "factors");
        this.dates = Objects.requireNonNull(dates, "dates");
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, String parameter) {
        if (player == null) {
            return null;
        }
        Optional<AuthState.Authenticated> in = sessions.apply(player.getUniqueId())
                .filter(AuthState.Authenticated.class::isInstance)
                .map(AuthState.Authenticated.class::cast);
        Optional<Account> account = in.map(AuthState.Authenticated::account);
        return switch (parameter.toLowerCase(Locale.ROOT)) {
            case "authenticated" -> String.valueOf(in.isPresent());
            case "method" -> in.map(found -> word(found.method())).orElse(NOTHING);
            case "premium" ->
                account.map(found -> String.valueOf(found.kind() == AccountKind.PREMIUM))
                        .orElse(NOTHING);
            case "kind" -> account.map(found -> word(found.kind())).orElse(NOTHING);
            case "2fa" ->
                account.flatMap(found -> factors.on(found.id()))
                        .map(String::valueOf)
                        .orElse(NOTHING);
            case "registered" ->
                account.map(found -> date(found.registeredAt())).orElse(NOTHING);
            case "last_login" ->
                account.flatMap(found -> Optional.ofNullable(found.lastLoginAt()))
                        .map(this::date)
                        .orElse(NOTHING);
            // Null, not an empty string: PlaceholderAPI leaves a key nobody owns as it was written, which is how an
            // operator finds their own typo.
            default -> null;
        };
    }

    private static String word(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private String date(Instant at) {
        return dates.get().format(at);
    }
}
