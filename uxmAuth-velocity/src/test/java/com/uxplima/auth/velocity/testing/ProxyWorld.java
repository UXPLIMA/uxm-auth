package com.uxplima.auth.velocity.testing;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.uxplima.auth.adapter.outbound.AuthDatabase;
import com.uxplima.auth.adapter.outbound.DoorWords;
import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.adapter.outbound.SqlAccountRepository;
import com.uxplima.auth.adapter.outbound.SqlDisputes;
import com.uxplima.auth.application.ReadTheName;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.premium.AtTheDoor;
import com.uxplima.auth.application.premium.LetPremiumIn;
import com.uxplima.auth.application.premium.Moves;
import com.uxplima.auth.application.premium.NameMemory;
import com.uxplima.auth.application.premium.PremiumLookup;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.premium.ConflictStrategy;
import com.uxplima.auth.domain.premium.DomainMark;
import com.uxplima.auth.domain.premium.HostPatterns;
import com.uxplima.auth.domain.premium.NameOwnership;
import com.uxplima.auth.domain.premium.NameRule;
import com.uxplima.auth.domain.premium.PremiumAnswer;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.premium.WhenUnknown;
import com.uxplima.auth.velocity.adapter.inbound.ProxyDoor;
import com.uxplima.auth.velocity.adapter.outbound.ProxyLocales;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.text.language.LanguageSettings;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * A proxy's door on a real SQLite file, with the web answer, the rule, the conflict strategy and the scheme a
 * test sets before it builds the door.
 */
public final class ProxyWorld implements AutoCloseable {

    public static final UUID MOJANG = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    public static final String PLAY = "play.example.org";
    public static final String PREMIUM_HOST = "premium.example.org";

    /** A clock a test moves on by hand. */
    public static final class MovingClock extends Clock {
        private Instant now = Instant.parse("2026-10-17T20:00:00Z");

        public void pass(Duration time) {
            now = now.plus(time);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    public final MovingClock clock = new MovingClock();
    public final Database database;
    public final SqlAccountRepository accounts;
    public final Messages messages = new Messages(
            new MessageCatalog(Map.of(Locale.ENGLISH, MessageCatalog.defaults(Keys.class)), Locale.ENGLISH),
            new ProxyLocales(() -> LanguageSettings.following(Locale.ENGLISH)));
    public final DoorWords words = new DoorWords(messages);
    public PremiumAnswer web = new PremiumAnswer.Premium(MOJANG, "Notch");
    public NameRule rule = NameRule.AUTOMATIC;
    public ConflictStrategy conflict = ConflictStrategy.RENAME;
    public UuidScheme scheme = UuidScheme.KEEP_FIRST;

    public ProxyWorld(Path folder) {
        this.database = AuthDatabase.open(
                new PluginSettings.Storage("jdbc:sqlite:" + folder.resolve("accounts.db"), "", "", 1), "proxy-test");
        this.accounts = new SqlAccountRepository(database);
    }

    /** The door, built with what the test set. */
    public ProxyDoor door() {
        SqlDisputes disputes = new SqlDisputes(database);
        NamePolicy policy = new NamePolicy(Pattern.compile("[A-Za-z0-9_]+"), 3, 16, List.of());
        AtTheDoor atTheDoor = new AtTheDoor(
                accounts,
                () -> policy,
                new PremiumLookup(() -> List.of(name -> web), () -> Duration.ZERO, clock),
                () -> new NameOwnership(
                        rule,
                        WhenUnknown.CRACKED,
                        new HostPatterns(List.of(PREMIUM_HOST)),
                        new HostPatterns(List.of())),
                new NameMemory(() -> Duration.ofMinutes(5), clock),
                new NameMemory(() -> Duration.ofMinutes(10), clock),
                new NameMemory(() -> Duration.ofMinutes(10), clock),
                disputes);
        return new ProxyDoor(
                atTheDoor,
                new LetPremiumIn(accounts, disputes, () -> conflict, () -> "_", scheme, clock),
                new Moves(accounts),
                new ReadTheName(accounts, () -> policy, () -> 3),
                () -> new DomainMark("+", false),
                scheme,
                clock);
    }

    @Override
    public void close() {
        database.close();
    }
}
