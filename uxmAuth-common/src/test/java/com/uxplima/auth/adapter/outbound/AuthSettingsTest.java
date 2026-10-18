package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.uxplima.auth.adapter.outbound.premium.PremiumSource;
import com.uxplima.auth.application.factor.SecondStep;
import com.uxplima.auth.application.factor.SetUpTotp;
import com.uxplima.auth.application.mail.MailCodes;
import com.uxplima.auth.application.staff.StaffAction;
import com.uxplima.auth.domain.factor.BackupCodes;
import com.uxplima.auth.domain.factor.Totp;
import com.uxplima.auth.domain.hash.HashAlgorithm;
import com.uxplima.auth.domain.mail.DomainRule;
import com.uxplima.auth.domain.network.AfterLogin;
import com.uxplima.auth.domain.network.Balance;
import com.uxplima.auth.domain.network.Route;
import com.uxplima.auth.domain.password.CharClass;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.premium.Arrival;
import com.uxplima.auth.domain.premium.ConflictStrategy;
import com.uxplima.auth.domain.premium.Decision;
import com.uxplima.auth.domain.premium.DomainMark;
import com.uxplima.auth.domain.premium.Holder;
import com.uxplima.auth.domain.premium.NameRule;
import com.uxplima.auth.domain.premium.PremiumAnswer;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.premium.WhenUnknown;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The blocks of config.conf that decide how a player gets in. */
final class AuthSettingsTest {

    @Test
    @DisplayName("a file that says nothing gets the defaults the plan and the brief chose")
    void defaultsMatchThisPlan(@TempDir Path folder) throws IOException {
        PluginSettings.Snapshot settings = settingsFrom(folder, "");

        assertThat(settings.names()).isEqualTo(new AuthSettings.Names("[A-Za-z0-9_]+", 3, 16, List.of(), false));
        assertThat(settings.passwords()).isEqualTo(new AuthSettings.Passwords(6, 64, "\\S+", Set.of(), true, true, 3));
        assertThat(settings.hashing())
                .isEqualTo(new AuthSettings.HashSettings(HashAlgorithm.ARGON2ID, 65536, 3, 1, 12, 600_000));
        assertThat(settings.lane()).isEqualTo(new AuthSettings.Lane(2, 4, 256));
        assertThat(settings.timeouts())
                .isEqualTo(new AuthSettings.Timeouts(
                        Duration.ofSeconds(90), Duration.ofSeconds(60), Duration.ofSeconds(5)));
        assertThat(settings.hold().movement()).isTrue();
        assertThat(settings.hold().allowedCommands()).isEmpty();
        assertThat(settings.hold().hideStats()).isTrue();
        assertThat(settings.bedrock()).isEqualTo(new AuthSettings.Bedrock(true));
        assertThat(settings.premium().sources())
                .containsExactly(
                        PremiumSource.MOJANG, PremiumSource.PLAYERDB, PremiumSource.MINETOOLS, PremiumSource.ASHCON);
        assertThat(settings.premium().keepAnswers()).isEqualTo(Duration.ofMinutes(30));
        assertThat(settings.premium().mojangCap()).isEqualTo(550);
        assertThat(settings.premium().timeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(settings.premium().address(PremiumSource.MOJANG))
                .isEqualTo("https://api.mojang.com/users/profiles/minecraft/{name}");
    }

    @Test
    @DisplayName("who owns a name defaults to the automatic rule, cracked when nobody can say, five minutes of memory")
    void ownershipDefaults(@TempDir Path folder) throws IOException {
        PluginSettings.Snapshot settings = settingsFrom(folder, "");

        assertThat(settings.ownership())
                .isEqualTo(new AuthSettings.Ownership(
                        NameRule.AUTOMATIC,
                        WhenUnknown.CRACKED,
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(10),
                        List.of(),
                        List.of(),
                        UuidScheme.KEEP_FIRST,
                        ConflictStrategy.RENAME,
                        "_",
                        new DomainMark("+", false)));
    }

    @Test
    @DisplayName("the domain rule's mark is read with its place, before or after the name")
    void theDomainMarkIsRead(@TempDir Path folder) throws IOException {
        PluginSettings.Snapshot settings = settingsFrom(folder, """
                ownership {
                  domains {
                    cracked-mark = "c_"
                    mark-at = start
                  }
                }
                """);

        assertThat(settings.ownership().crackedMark()).isEqualTo(new DomainMark("c_", true));
        assertThatThrownBy(() -> settingsFrom(folder, "ownership { domains { mark-at = middle } }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownership.domains.mark-at");
    }

    @Test
    @DisplayName("the conflict strategy is read in the words config.conf uses, and a rename mark cannot be empty")
    void theConflictStrategyIsRead(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "ownership { conflict = premium-takes }\n")
                        .ownership()
                        .conflict())
                .isEqualTo(ConflictStrategy.PREMIUM_TAKES);
        assertThatThrownBy(() -> settingsFrom(folder, "ownership { rename-mark = \"\" }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownership.rename-mark");
    }

    @Test
    @DisplayName("a rename mark the name pattern refuses is refused, since a renamed player types it to log in")
    void aRenameMarkMustPassTheNamePattern(@TempDir Path folder) throws IOException {
        assertThatThrownBy(() -> settingsFrom(folder, "ownership { rename-mark = \"+\" }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownership.rename-mark")
                .hasMessageContaining("names.pattern");
        assertThat(settingsFrom(folder, "names { pattern = \"[A-Za-z0-9_+]+\" }\nownership { rename-mark = \"+\" }\n")
                        .ownership()
                        .renameMark())
                .isEqualTo("+");
    }

    @Test
    @DisplayName("the UUID scheme is read in the words config.conf uses, and an unknown one is refused")
    void theUuidSchemeIsRead(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "ownership { uuid-scheme = mojang }\n")
                        .ownership()
                        .uuidScheme())
                .isEqualTo(UuidScheme.MOJANG);
        assertThatThrownBy(() -> settingsFrom(folder, "ownership { uuid-scheme = by-name }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownership.uuid-scheme")
                .hasMessageContaining("keep-first");
    }

    @Test
    @DisplayName("the rule and the failure policy are read in the words config.conf uses")
    void ownershipReadsItsWords(@TempDir Path folder) throws IOException {
        PluginSettings.Snapshot settings = settingsFrom(folder, """
                ownership {
                  rule = opt-in
                  when-every-source-fails = refuse
                }
                """);

        assertThat(settings.ownership().rule()).isEqualTo(NameRule.OPT_IN);
        assertThat(settings.ownership().whenUnknown()).isEqualTo(WhenUnknown.REFUSE);
    }

    @Test
    @DisplayName("a rule this plugin does not have is refused, naming the line and the four it has")
    void anUnknownRuleIsRefused(@TempDir Path folder) {
        assertThatThrownBy(() -> settingsFrom(folder, "ownership { rule = mixed }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownership.rule")
                .hasMessageContaining("mixed")
                .hasMessageContaining("opt-in");
    }

    @Test
    @DisplayName("the domain rule without an address for paid accounts is refused, since nobody could prove a name")
    void theDomainRuleNeedsAPremiumAddress(@TempDir Path folder) {
        assertThatThrownBy(() -> settingsFrom(folder, "ownership { rule = domain }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownership.domains.premium");
    }

    @Test
    @DisplayName("the operator's rule is the decision the door makes")
    void theRuleIsTheDecision(@TempDir Path folder) throws IOException {
        PluginSettings.Snapshot settings = settingsFrom(folder, """
                ownership {
                  rule = domain
                  domains { premium = ["premium.example.org"] }
                }
                """);

        assertThat(settings.ownership()
                        .decision()
                        .decide(
                                new Arrival(Holder.NOBODY, false, false, "premium.example.org"),
                                PremiumAnswer.Unknown::new))
                .isEqualTo(new Decision.Handshake());
    }

    @Test
    @DisplayName("the operator's order of the premium sources is kept, and a source left out is not asked")
    void premiumSourcesFollowTheFile(@TempDir Path folder) throws IOException {
        PluginSettings.Snapshot settings = settingsFrom(folder, "premium { sources = [ashcon, \"Mojang\"] }\n");

        assertThat(settings.premium().sources()).containsExactly(PremiumSource.ASHCON, PremiumSource.MOJANG);
    }

    @Test
    @DisplayName("a premium source this plugin does not know is refused, naming the line and the word")
    void anUnknownPremiumSourceIsRefused(@TempDir Path folder) {
        assertThatThrownBy(() -> settingsFrom(folder, "premium { sources = [mojang, namemc] }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("premium.sources")
                .hasMessageContaining("namemc");
    }

    @Test
    @DisplayName("a Mojang cap at or past Mojang's own limit of 600 is refused, since Mojang would refuse first")
    void aMojangCapPastTheLimitIsRefused(@TempDir Path folder) {
        assertThatThrownBy(() -> settingsFrom(folder, "premium { mojang-cap-per-10-minutes = 600 }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("premium.mojang-cap-per-10-minutes");
    }

    @Test
    @DisplayName("a source address without the {name} it is asked about is refused")
    void anAddressWithoutTheNameIsRefused(@TempDir Path folder) {
        assertThatThrownBy(() -> settingsFrom(folder, "premium { addresses { ashcon = \"https://example.org/\" } }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("premium.addresses.ashcon");
    }

    @Test
    @DisplayName("the shipped file says exactly what the code defaults to")
    void theShippedFileSaysTheDefaults(@TempDir Path folder) throws IOException {
        Path shipped = folder.resolve("shipped");
        Files.createDirectories(shipped);
        try (InputStream file = Objects.requireNonNull(getClass().getResourceAsStream("/config.conf"))) {
            Files.copy(file, shipped.resolve("config.conf"));
        }
        PluginSettings.Snapshot fromShipped = new PluginSettings(shipped.resolve("config.conf")).current();
        PluginSettings.Snapshot fromNothing = settingsFrom(shipped, "");

        assertThat(fromShipped).isEqualTo(fromNothing);
    }

    @Test
    @DisplayName("an algorithm this plugin does not have is refused, naming the line")
    void anUnknownHashAlgorithmIsRefusedWithItsPath(@TempDir Path folder) {
        assertThatThrownBy(() -> settingsFrom(folder, "hashing { algorithm = \"md5\" }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hashing.algorithm")
                .hasMessageContaining("md5");
    }

    @Test
    @DisplayName("bcrypt with a maximum past its 72 bytes is refused, because the rest would be ignored")
    void bcryptWithAMaximumOver72BytesIsRefused(@TempDir Path folder) {
        assertThatThrownBy(() -> settingsFrom(folder, """
                        hashing { algorithm = "bcrypt" }
                        passwords { max-length = 73 }
                        """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("passwords.max-length")
                .hasMessageContaining("72");
    }

    @Test
    @DisplayName("bcrypt within its limit also caps the bytes, so a long password in a wide script is told why")
    void bcryptCapsTheBytes(@TempDir Path folder) throws IOException {
        PluginSettings.Snapshot settings = settingsFrom(folder, """
                hashing { algorithm = "bcrypt" }
                passwords { max-length = 64 }
                """);

        assertThat(settings.passwordPolicy().check(Password.of("ş".repeat(40))))
                .isEqualTo(new PasswordVerdict.TooLongForTheHash(72));
    }

    @Test
    @DisplayName("a pattern that does not compile is refused, naming the line")
    void aBrokenPatternIsRefused(@TempDir Path folder) {
        assertThatThrownBy(() -> settingsFrom(folder, "names { pattern = \"[a-z\" }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names.pattern");
    }

    @Test
    @DisplayName("the required classes are read by their names")
    void readsTheRequiredClasses(@TempDir Path folder) throws IOException {
        PluginSettings.Snapshot settings = settingsFrom(folder, "passwords { required = [\"digit\", \"upper\"] }\n");

        assertThat(settings.passwords().required()).containsExactlyInAnyOrder(CharClass.DIGIT, CharClass.UPPER);
    }

    @Test
    @DisplayName("an effect list the operator emptied stays empty, and one never written is the shipped one")
    void effectsKeepAnEmptyList(@TempDir Path folder) throws IOException {
        PluginSettings.Snapshot settings = settingsFrom(folder, "effects { logged-in = [] }\n");

        assertThat(settings.effects().of(AuthSettings.Moment.LOGGED_IN)).isEmpty();
        assertThat(settings.effects().of(AuthSettings.Moment.REGISTERED)).isNotEmpty();
        assertThat(settings.effects().of(AuthSettings.Moment.REMINDER_CODE))
                .containsExactly("[actionbar] @reminder.code");
    }

    @Test
    @DisplayName("a reload swaps in the whole new file at once")
    void reloadSwapsTheWholeSnapshot(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, "passwords { min-length = 6 }\nlane { threads = 2 }\n", StandardCharsets.UTF_8);
        PluginSettings settings = new PluginSettings(file);
        PluginSettings.Snapshot before = settings.current();

        Files.writeString(file, "passwords { min-length = 8 }\nlane { threads = 3 }\n", StandardCharsets.UTF_8);
        settings.reload();

        assertThat(before.passwords().minLength()).isEqualTo(6);
        assertThat(settings.current().passwords().minLength()).isEqualTo(8);
        assertThat(settings.current().lane().threads()).isEqualTo(3);
    }

    @Test
    @DisplayName("a reload that fails keeps the snapshot that worked")
    void aBrokenReloadKeepsTheOldSnapshot(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, "", StandardCharsets.UTF_8);
        PluginSettings settings = new PluginSettings(file);
        PluginSettings.Snapshot before = settings.current();

        Files.writeString(file, "hashing { algorithm = \"md5\" }\n", StandardCharsets.UTF_8);

        assertThatThrownBy(settings::reload).isInstanceOf(IllegalArgumentException.class);
        assertThat(settings.current()).isSameAs(before);
    }

    @Test
    @DisplayName("a join shows its title and subtitle together, as the one subtitle line the engine reads both from")
    void theJoinTitleAndSubtitleAreOneLine(@TempDir Path folder) throws IOException {
        PluginSettings.Snapshot settings = settingsFrom(folder, "");

        for (AuthSettings.Moment moment :
                List.of(AuthSettings.Moment.JOIN_UNREGISTERED, AuthSettings.Moment.JOIN_REGISTERED)) {
            assertThat(settings.effects().of(moment))
                    .describedAs("a [title] line and a [subtitle] line are two titles, the second over the first")
                    .noneMatch(line -> line.startsWith("[title]"))
                    .anyMatch(line -> line.startsWith("[subtitle]") && line.contains("|"));
        }
    }

    @Test
    @DisplayName("a server stands alone unless network.enabled says otherwise, and a claim is fresh for 30 seconds")
    void theNetworkIsOffByDefault(@TempDir Path folder) throws IOException {
        AuthSettings.Network network = settingsFrom(folder, "").network();

        assertThat(network.enabled()).isFalse();
        assertThat(network.secret()).isEmpty();
        assertThat(network.claimFor()).isEqualTo(Duration.ofSeconds(30));
        assertThat(network.allowedCommands()).isEmpty();
    }

    @Test
    @DisplayName("a network has no auth server and no lobby until the operator names them, and balances by players")
    void theRoutingDefaults(@TempDir Path folder) throws IOException {
        AuthSettings.Routing routing = settingsFrom(folder, "").network().routing();

        assertThat(routing.authServers()).isEmpty();
        assertThat(routing.lobbies()).isEmpty();
        assertThat(routing.balance()).isEqualTo(Balance.LEAST_PLAYERS);
        assertThat(routing.afterLogin()).isEqualTo(AfterLogin.LOBBY);
        assertThat(routing.byDomain()).isEmpty();
    }

    @Test
    @DisplayName("the routes by domain are read in the order written, and a route without a server is refused")
    void theRoutesAreRead(@TempDir Path folder) throws IOException {
        AuthSettings.Routing routing = settingsFrom(folder, """
                        network {
                          auth-servers = [auth-1]
                          lobbies = [lobby-1, lobby-2]
                          balance = random
                          after-login = by-domain
                          by-domain = [
                            { host = "eu.*", server = lobby-eu }
                            { host = "*", server = lobby-1 }
                          ]
                        }
                        """).network().routing();

        assertThat(routing.authServers()).containsExactly("auth-1");
        assertThat(routing.lobbies()).containsExactly("lobby-1", "lobby-2");
        assertThat(routing.balance()).isEqualTo(Balance.RANDOM);
        assertThat(routing.afterLogin()).isEqualTo(AfterLogin.BY_DOMAIN);
        assertThat(routing.byDomain()).containsExactly(new Route("eu.*", "lobby-eu"), new Route("*", "lobby-1"));
        assertThatThrownBy(() -> settingsFrom(folder, "network { by-domain = [ { host = \"eu.*\" } ] }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("network.by-domain");
    }

    @Test
    @DisplayName("a waiting player waits on a server unless the proxy is told to keep them in a LimboAPI world")
    void theWaitIsOnAServerByDefault(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "").network().waitIn()).isEqualTo(AuthSettings.WaitIn.SERVER);
        assertThat(settingsFrom(folder, "").network().limboFile()).isEqualTo("limbo.schem");
        assertThat(settingsFrom(folder, "network { wait-in = limbo }\n")
                        .network()
                        .waitIn())
                .isEqualTo(AuthSettings.WaitIn.LIMBO);
    }

    @Test
    @DisplayName("the waiting world's spawn is read as x, y, z and a facing, and a spawn short of a point is refused")
    void theLimboSpawnIsRead(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "").network().limboSpawn())
                .isEqualTo(new AuthSettings.Spot(0.5, 100, 0.5, 0, 0));
        assertThat(settingsFrom(folder, "network { limbo-spawn = [10, 70, -3.5, 90, 10] }\n")
                        .network()
                        .limboSpawn())
                .isEqualTo(new AuthSettings.Spot(10, 70, -3.5, 90, 10));
        assertThat(settingsFrom(folder, "network { limbo-spawn = [1, 2, 3] }\n")
                        .network()
                        .limboSpawn())
                .isEqualTo(new AuthSettings.Spot(1, 2, 3, 0, 0));
        assertThatThrownBy(() -> settingsFrom(folder, "network { limbo-spawn = [1, 2] }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("network.limbo-spawn");
    }

    @Test
    @DisplayName("Redis is off until the operator writes its address, and the address is never printed")
    void redisIsOffByDefault(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "").network().redis()).isEqualTo(new AuthSettings.Redis("", "uxmauth"));
        assertThat(settingsFrom(folder, "").network().redis().on()).isFalse();
        AuthSettings.Redis written = settingsFrom(
                        folder, "network { redis { uri = \"redis://:pw@10.0.0.9:6379\", channel = \"eu\" } }\n")
                .network()
                .redis();
        assertThat(written).isEqualTo(new AuthSettings.Redis("redis://:pw@10.0.0.9:6379", "eu"));
        assertThat(written.on()).isTrue();
        assertThat(written.toString()).doesNotContain("pw").doesNotContain("10.0.0.9");
        assertThatThrownBy(() -> settingsFrom(folder, "network { redis { channel = \" \" } }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("network.redis.channel");
    }

    @Test
    @DisplayName("sessions are for those who choose them, an hour long, and a mode not on the list is refused")
    void sessionsAreRead(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "").protection().sessions())
                .isEqualTo(new com.uxplima.auth.application.protection.Remember.Settings(
                        com.uxplima.auth.application.protection.Remember.Mode.CHOSEN,
                        java.time.Duration.ofMinutes(60)));
        assertThat(settingsFrom(folder, "sessions { mode = everyone, minutes = 15 }\n")
                        .protection()
                        .sessions())
                .isEqualTo(new com.uxplima.auth.application.protection.Remember.Settings(
                        com.uxplima.auth.application.protection.Remember.Mode.EVERYONE,
                        java.time.Duration.ofMinutes(15)));
        assertThatThrownBy(() -> settingsFrom(folder, "sessions { mode = always }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessions.mode");
        assertThatThrownBy(() -> settingsFrom(folder, "sessions { minutes = 0 }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessions.minutes");
    }

    @Test
    @DisplayName("bans climb 5 minutes, 30, an hour, a day after three failures and forgive after a quiet day")
    void bansAreRead(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "").protection().bans())
                .isEqualTo(new com.uxplima.auth.domain.protection.BanSteps(
                        java.util.List.of(
                                java.time.Duration.ofMinutes(5),
                                java.time.Duration.ofMinutes(30),
                                java.time.Duration.ofMinutes(60),
                                java.time.Duration.ofMinutes(1440)),
                        3,
                        java.time.Duration.ofHours(24)));
        assertThat(settingsFrom(
                                folder,
                                "protection { bans { steps-minutes = [1, 2], failures = 5, forgive-after-hours = 6 } }\n")
                        .protection()
                        .bans())
                .isEqualTo(new com.uxplima.auth.domain.protection.BanSteps(
                        java.util.List.of(java.time.Duration.ofMinutes(1), java.time.Duration.ofMinutes(2)),
                        5,
                        java.time.Duration.ofHours(6)));
        assertThatThrownBy(() -> settingsFrom(folder, "protection { bans { steps-minutes = [] } }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protection.bans");
        assertThatThrownBy(() -> settingsFrom(folder, "protection { bans { failures = 0 } }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protection.bans");
    }

    @Test
    @DisplayName(
            "the common-password file is read beside the config, again at every reload, and the breach check is off")
    void commonPasswordsAreRead(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, "passwords { common-file = \"lists/weak.txt\" }\n", StandardCharsets.UTF_8);
        Files.createDirectories(folder.resolve("lists"));
        Files.writeString(folder.resolve("lists/weak.txt"), "# weak\npassword\nletmein\n", StandardCharsets.UTF_8);
        PluginSettings settings = new PluginSettings(file);

        assertThat(settings.current().passwordLists().breachCheck()).isFalse();
        assertThat(settings.current().passwordPolicy().check(com.uxplima.auth.domain.password.Password.of("LetMeIn")))
                .isEqualTo(new com.uxplima.auth.domain.password.PasswordVerdict.Common());

        Files.writeString(folder.resolve("lists/weak.txt"), "dragon123\n", StandardCharsets.UTF_8);
        settings.reload();

        assertThat(settings.current().passwordPolicy().check(com.uxplima.auth.domain.password.Password.of("letmein")))
                .isEqualTo(new com.uxplima.auth.domain.password.PasswordVerdict.Accepted());
        assertThat(settings.current().passwordPolicy().check(com.uxplima.auth.domain.password.Password.of("Dragon123")))
                .isEqualTo(new com.uxplima.auth.domain.password.PasswordVerdict.Common());
        assertThat(settingsFrom(folder, "passwords { breach-check = true }\n")
                        .passwordLists()
                        .breachCheck())
                .isTrue();
    }

    @Test
    @DisplayName("a common-password file that is not there is no list, and the shipped one is used by the shipped file")
    void aMissingListIsNone(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "").passwordLists().commonFile()).isEqualTo("passwords/common.txt");
        assertThat(settingsFrom(folder, "")
                        .passwordPolicy()
                        .check(com.uxplima.auth.domain.password.Password.of("password")))
                .isEqualTo(new com.uxplima.auth.domain.password.PasswordVerdict.Accepted());
    }

    @Test
    @DisplayName("countries are off by default, and the operator's mode, list, licence and refresh are read")
    void countriesAreRead(@TempDir Path folder) throws IOException {
        com.uxplima.auth.adapter.outbound.geo.GeoSettings shipped =
                settingsFrom(folder, "").protection().countries();
        assertThat(shipped.rule().mode()).isEqualTo(com.uxplima.auth.application.protection.CountryRule.Mode.OFF);
        assertThat(shipped.rule().unknownAllowed()).isTrue();
        assertThat(shipped.licence().present()).isFalse();
        assertThat(shipped.refresh()).isEqualTo(java.time.Duration.ofDays(7));

        com.uxplima.auth.adapter.outbound.geo.GeoSettings written = settingsFrom(
                        folder,
                        "protection { countries { mode = deny, list = [\"ru\", \"CN\"], unknown = deny,"
                                + " allow-addresses = [\"10.0.0.0/8\"], account-id = \"123\","
                                + " licence-key = \"abc\", refresh-days = 3 } }\n")
                .protection()
                .countries();
        assertThat(written.rule())
                .isEqualTo(new com.uxplima.auth.application.protection.CountryRule.Settings(
                        com.uxplima.auth.application.protection.CountryRule.Mode.DENY,
                        java.util.Set.of("RU", "CN"),
                        new com.uxplima.auth.domain.protection.AddressRanges(java.util.List.of("10.0.0.0/8")),
                        false));
        assertThat(written.licence())
                .isEqualTo(new com.uxplima.auth.adapter.outbound.geo.GeoSettings.Licence("123", "abc"));
        assertThat(written.refresh()).isEqualTo(java.time.Duration.ofDays(3));
        assertThatThrownBy(() -> settingsFrom(folder, "protection { countries { mode = sometimes } }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protection.countries");
        assertThatThrownBy(() -> settingsFrom(folder, "protection { countries { unknown = maybe } }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protection.countries.unknown");
    }

    @Test
    @DisplayName(
            "the captcha is off by default, and the operator's moment, count, length, characters and screen are read")
    void theCaptchaIsRead(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "").protection().captcha())
                .isEqualTo(new com.uxplima.auth.application.protection.Captchas.Settings(
                        com.uxplima.auth.application.protection.Captchas.When.OFF,
                        3,
                        5,
                        "ABCDEFGHJKMNPQRSTUVWXYZ23456789",
                        java.time.Duration.ofMinutes(60),
                        com.uxplima.auth.application.protection.Captchas.JavaScreen.DIALOG));

        assertThat(settingsFrom(
                                folder,
                                "protection { captcha { when = both, after-failures = 2, length = 6,"
                                        + " characters = \"123\", failure-memory-minutes = 5, java = map } }\n")
                        .protection()
                        .captcha())
                .isEqualTo(new com.uxplima.auth.application.protection.Captchas.Settings(
                        com.uxplima.auth.application.protection.Captchas.When.BOTH,
                        2,
                        6,
                        "123",
                        java.time.Duration.ofMinutes(5),
                        com.uxplima.auth.application.protection.Captchas.JavaScreen.MAP));
        assertThatThrownBy(() -> settingsFrom(folder, "protection { captcha { when = sometimes } }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protection.captcha");
        assertThatThrownBy(() -> settingsFrom(folder, "protection { captcha { java = book } }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protection.captcha");
        assertThatThrownBy(() -> settingsFrom(folder, "protection { captcha { length = 9 } }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protection.captcha");
    }

    @Test
    @DisplayName(
            "the app's name, line, code shape, setup time and backup codes are read, with the defaults every app speaks")
    void theSecondFactorIsRead(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "").secondFactor().setup())
                .isEqualTo(new SetUpTotp.Settings(
                        "Minecraft",
                        "{player}",
                        new Totp(6, Duration.ofSeconds(30), 1),
                        Duration.ofMinutes(10),
                        new BackupCodes(8, 10)));

        assertThat(settingsFrom(
                                folder,
                                "second-factor { issuer = \"Uxplima\", label = \"mc {player}\", digits = 8,"
                                        + " period-seconds = 60, window-steps = 2, setup-minutes = 3,"
                                        + " backup-codes = 12, backup-code-length = 14 }\n")
                        .secondFactor()
                        .setup())
                .isEqualTo(new SetUpTotp.Settings(
                        "Uxplima",
                        "mc {player}",
                        new Totp(8, Duration.ofSeconds(60), 2),
                        Duration.ofMinutes(3),
                        new BackupCodes(12, 14)));
        assertThat(settingsFrom(folder, "").secondFactor().step()).isEqualTo(new SecondStep.Settings(true, false, 3));
        assertThat(settingsFrom(folder, """
                                second-factor { premium-skips = false }
                                sessions { skip-second-factor = true }
                                passwords { max-attempts = 5 }
                                """).secondFactor().step()).isEqualTo(new SecondStep.Settings(false, true, 5));
        assertThatThrownBy(() -> settingsFrom(folder, "second-factor { digits = 5 }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("second-factor");
        assertThatThrownBy(() -> settingsFrom(folder, "second-factor { issuer = \"\" }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("second-factor.issuer");
        assertThat(settingsFrom(folder, "").secondFactor().requiredPermission()).isEqualTo("uxmauth.2fa.required");
        assertThat(settingsFrom(folder, "second-factor { required-permission = \" staff.app \" }\n")
                        .secondFactor()
                        .requiredPermission())
                .isEqualTo("staff.app");
        assertThat(settingsFrom(folder, "second-factor { required-permission = \"\" }\n")
                        .secondFactor()
                        .requiredPermission())
                .isEmpty();
        assertThatThrownBy(() -> settingsFrom(folder, "second-factor { backup-codes = 0 }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("second-factor.backup-codes");
    }

    @Test
    @DisplayName(
            "mail is off by default; the server, its security, the login and the sender are read, the password never printed")
    void mailIsRead(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "").mail())
                .isEqualTo(new MailSettings(
                        false,
                        "",
                        587,
                        MailSettings.Security.STARTTLS,
                        "",
                        "",
                        "",
                        "Minecraft",
                        Duration.ofSeconds(10),
                        2));

        MailSettings read = settingsFrom(folder, """
                        mail {
                          enabled = true, host = "smtp.example.com", port = 465, security = ssl
                          username = "mailer", password = "s3cret", from-address = "auth@example.com"
                          from-name = "Example", timeout-seconds = 20
                        }
                        """).mail();
        assertThat(read)
                .isEqualTo(new MailSettings(
                        true,
                        "smtp.example.com",
                        465,
                        MailSettings.Security.SSL,
                        "mailer",
                        "s3cret",
                        "auth@example.com",
                        "Example",
                        Duration.ofSeconds(20),
                        2));
        assertThat(read.toString()).doesNotContain("s3cret");
        assertThat(settingsFrom(folder, "mail { security = plain }\n").mail().security())
                .isEqualTo(MailSettings.Security.PLAIN);
        assertThatThrownBy(() -> settingsFrom(folder, "mail { security = tls13 }\n"))
                .hasMessageContaining("mail.security");
        assertThatThrownBy(() -> settingsFrom(folder, "mail { enabled = true, from-address = \"a@example.com\" }\n"))
                .hasMessageContaining("mail.host");
        assertThatThrownBy(() -> settingsFrom(folder, "mail { enabled = true, host = \"smtp.example.com\" }\n"))
                .hasMessageContaining("mail.from-address");
        assertThatThrownBy(() -> settingsFrom(folder, "mail { port = 0 }\n")).hasMessageContaining("mail.port");
        assertThatThrownBy(() -> settingsFrom(folder, "mail { threads = 0 }\n")).hasMessageContaining("mail.threads");
    }

    @Test
    @DisplayName("every domain, fifteen-minute codes of six digits with five tries and a minute between, and no"
            + " address required, by default")
    void emailIsRead(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "").email())
                .isEqualTo(new EmailSettings(
                        new DomainRule(List.of(), List.of()),
                        new MailCodes.Settings(Duration.ofMinutes(15), 5, Duration.ofSeconds(60), 6),
                        false));
        assertThat(settingsFrom(folder, """
                                email {
                                  allowed-domains = ["example.com"], denied-domains = ["temp.example.com"]
                                  code-minutes = 5, code-attempts = 3, cooldown-seconds = 120, code-length = 8
                                  required-at-registration = true
                                }
                                """).email())
                .isEqualTo(new EmailSettings(
                        new DomainRule(List.of("example.com"), List.of("temp.example.com")),
                        new MailCodes.Settings(Duration.ofMinutes(5), 3, Duration.ofMinutes(2), 8),
                        true));
        assertThatThrownBy(() -> settingsFrom(folder, "email { code-attempts = 0 }\n"))
                .hasMessageContaining("email.code-attempts");
        assertThatThrownBy(() -> settingsFrom(folder, "email { denied-domains = [\"no domain\"] }\n"))
                .hasMessageContaining("email.denied-domains");
    }

    @Test
    @DisplayName("logins are kept ninety days by default, as long as the operator says, and never less than a day")
    void historyIsRead(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "").staff().historyKeep()).isEqualTo(Duration.ofDays(90));
        assertThat(settingsFrom(folder, "history { keep-days = 7 }\n").staff().historyKeep())
                .isEqualTo(Duration.ofDays(7));
        assertThatThrownBy(() -> settingsFrom(folder, "history { keep-days = 0 }\n"))
                .hasMessageContaining("history.keep-days");
    }

    @Test
    @DisplayName("staff read ten recent logins, twenty other accounts and dates as year-month-day hour:minute by"
            + " default, and a pattern that is no date is refused by name")
    void staffViewIsRead(@TempDir Path folder) throws IOException {
        StaffSettings shipped = settingsFrom(folder, "").staff();
        assertThat(shipped.info()).isEqualTo(new com.uxplima.auth.application.staff.AccountInfo.Settings(10, 20));
        assertThat(shipped.dates().format(java.time.LocalDateTime.of(2026, 10, 18, 9, 5)))
                .isEqualTo("2026-10-18 09:05");
        StaffSettings written = settingsFrom(
                        folder, "staff { recent-logins = 3, alts-shown = 5, date-format = \"dd.MM.yyyy\" }\n")
                .staff();
        assertThat(written.info()).isEqualTo(new com.uxplima.auth.application.staff.AccountInfo.Settings(3, 5));
        assertThat(written.dates().format(java.time.LocalDate.of(2026, 10, 18))).isEqualTo("18.10.2026");
        assertThatThrownBy(() -> settingsFrom(folder, "staff { date-format = \"{{nope\" }\n"))
                .hasMessageContaining("staff.date-format");
    }

    @Test
    @DisplayName("a destructive staff command runs in game only when staff.in-game names it; others always do")
    void inGameStaffCommandsAreRead(@TempDir Path folder) throws IOException {
        StaffSettings shipped = settingsFrom(folder, "").staff();
        assertThat(shipped.inGame()).isEmpty();
        assertThat(shipped.allowsInGame(StaffAction.UNREGISTER)).isFalse();
        assertThat(shipped.allowsInGame(StaffAction.UNBAN)).isTrue();
        assertThat(shipped.allowsInGame(StaffAction.FORCE_REGISTER)).isTrue();
        // Both hand an account to whoever stands at the door or holds the paid name: a staff account somebody
        // took over must not reach them from the game.
        assertThat(shipped.allowsInGame(StaffAction.FORCE_LOGIN)).isFalse();
        assertThat(shipped.allowsInGame(StaffAction.MARK_PREMIUM)).isFalse();

        StaffSettings opened = settingsFrom(folder, "staff { in-game = [unregister, resolve] }\n")
                .staff();
        assertThat(opened.inGame()).containsExactlyInAnyOrder(StaffAction.UNREGISTER, StaffAction.RESOLVE);
        assertThat(opened.allowsInGame(StaffAction.UNREGISTER)).isTrue();
        assertThat(opened.allowsInGame(StaffAction.SET_PASSWORD)).isFalse();
        assertThatThrownBy(() -> settingsFrom(folder, "staff { in-game = [unregistre] }\n"))
                .hasMessageContaining("staff.in-game")
                .hasMessageContaining("unregistre");
    }

    @Test
    @DisplayName("every alert is on by default, failed logins once a minute and an address told at three accounts;"
            + " each is switched by name, and a threshold of one is refused by name")
    void alertsAreRead(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "").staff().alerts())
                .isEqualTo(new com.uxplima.auth.application.staff.Alerts.Settings(
                        true, Duration.ofSeconds(60), true, 3, true));
        assertThat(settingsFrom(
                                folder,
                                "alerts { failed-login = false, failed-login-window-seconds = 300, ban = false,"
                                        + " shared-address-accounts = 0, staff-actions = false }\n")
                        .staff()
                        .alerts())
                .isEqualTo(new com.uxplima.auth.application.staff.Alerts.Settings(
                        false, Duration.ofMinutes(5), false, 0, false));
        assertThatThrownBy(() -> settingsFrom(folder, "alerts { shared-address-accounts = 1 }\n"))
                .hasMessageContaining("alerts.shared-address-accounts");
        assertThatThrownBy(() -> settingsFrom(folder, "alerts { failed-login-window-seconds = 0 }\n"))
                .hasMessageContaining("alerts.failed-login-window-seconds");
    }

    @Test
    @DisplayName("placeholder dates read year-month-day hour:minute by default, the operator's pattern otherwise,"
            + " and a pattern that is no date is refused by name")
    void placeholderDatesAreRead(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "")
                        .placeholders()
                        .dates()
                        .format(java.time.LocalDateTime.of(2026, 10, 18, 9, 5)))
                .isEqualTo("2026-10-18 09:05");
        assertThat(settingsFrom(folder, "placeholders { date-format = \"dd.MM.yyyy\" }\n")
                        .placeholders()
                        .dates()
                        .format(java.time.LocalDate.of(2026, 10, 18)))
                .isEqualTo("18.10.2026");
        assertThatThrownBy(() -> settingsFrom(folder, "placeholders { date-format = \"{{nope\" }\n"))
                .hasMessageContaining("placeholders.date-format");
    }

    @Test
    @DisplayName("Discord sends nothing until the operator gives a url; every event is on with its own message, each"
            + " switched and written by the operator, and a url that is no web address is refused by name")
    void discordIsRead(@TempDir Path folder) throws IOException {
        DiscordSettings shipped = settingsFrom(folder, "").discord();
        assertThat(shipped.on()).isFalse();
        assertThat(shipped.events()).containsOnlyKeys(DiscordSettings.Event.values());
        assertThat(shipped.events().values()).allMatch(DiscordSettings.Notice::enabled);
        assertThat(shipped.notice(DiscordSettings.Event.LOGIN).message()).contains("{player}", "{method}");

        DiscordSettings written = settingsFrom(
                        folder,
                        "discord { url = \"https://discord.com/api/webhooks/1/abc\", server = \"lobby\","
                                + " events { ban { enabled = false }, login { message = \"{player} in\" } } }\n")
                .discord();
        assertThat(written.on()).isTrue();
        assertThat(written.server()).isEqualTo("lobby");
        assertThat(written.notice(DiscordSettings.Event.BAN).enabled()).isFalse();
        assertThat(written.notice(DiscordSettings.Event.LOGIN))
                .isEqualTo(new DiscordSettings.Notice(true, "{player} in"));
        assertThatThrownBy(() -> settingsFrom(folder, "discord { url = \"not a url\" }\n"))
                .hasMessageContaining("discord.url");
        assertThatThrownBy(() -> settingsFrom(folder, "discord { url = \"ftp://example.org/x\" }\n"))
                .hasMessageContaining("discord.url");
    }

    @Test
    @DisplayName("three password accounts a day per address by default, with exempt addresses")
    void accountsAreRead(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "").protection().accounts())
                .isEqualTo(new com.uxplima.auth.application.protection.AccountCap.Settings(
                        3,
                        java.time.Duration.ofHours(24),
                        new com.uxplima.auth.domain.protection.AddressRanges(java.util.List.of())));
        assertThat(settingsFrom(
                                folder,
                                "protection { accounts { per-address = 1, window-hours = 2,"
                                        + " exempt-addresses = [\"10.0.0.1\"] } }\n")
                        .protection()
                        .accounts())
                .isEqualTo(new com.uxplima.auth.application.protection.AccountCap.Settings(
                        1,
                        java.time.Duration.ofHours(2),
                        new com.uxplima.auth.domain.protection.AddressRanges(java.util.List.of("10.0.0.1"))));
        assertThatThrownBy(() -> settingsFrom(folder, "protection { accounts { window-hours = 0 } }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protection.accounts");
    }

    @Test
    @DisplayName("five connections a minute per address by default, with the exempt addresses read as ranges")
    void connectionsAreRead(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "").protection().connections())
                .isEqualTo(new com.uxplima.auth.application.protection.ConnectionWindow.Settings(
                        5,
                        java.time.Duration.ofSeconds(60),
                        new com.uxplima.auth.domain.protection.AddressRanges(java.util.List.of())));
        com.uxplima.auth.application.protection.ConnectionWindow.Settings written = settingsFrom(
                        folder,
                        "protection { connections { per-address = 2, window-seconds = 10,"
                                + " exempt-addresses = [\"10.0.0.0/8\"] } }\n")
                .protection()
                .connections();
        assertThat(written.perAddress()).isEqualTo(2);
        assertThat(written.window()).isEqualTo(java.time.Duration.ofSeconds(10));
        assertThat(written.exempt().contains("10.9.9.9")).isTrue();
        assertThatThrownBy(() -> settingsFrom(folder, "protection { connections { per-address = 0 } }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protection.connections");
        assertThatThrownBy(() -> settingsFrom(folder, "protection { connections { exempt-addresses = [\"x\"] } }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protection.connections");
    }

    @Test
    @DisplayName("no connection is through a shared-address filter until the operator lists its addresses")
    void sharedHostsAreListed(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "").protection().sharedAddresses().through("198.51.100.1", "mc.tcpshield.com"))
                .isFalse();
        com.uxplima.auth.domain.protection.SharedAddresses listed = settingsFrom(
                        folder,
                        "protection { shared-address-hosts = [\"*.tcpshield.com\"],"
                                + " shared-address-sources = [\"198.51.100.0/24\"] }\n")
                .protection()
                .sharedAddresses();
        assertThat(listed.through("198.51.100.1", "mc.tcpshield.com:25565")).isTrue();
        assertThat(listed.through("203.0.113.7", "mc.tcpshield.com:25565")).isFalse();
        assertThat(settingsFrom(folder, "protection { shared-address-hosts = [\"*.tcpshield.com\"] }\n")
                        .protection()
                        .sharedAddresses()
                        .unproven())
                .isTrue();
        assertThat(settingsFrom(folder, "protection { shared-address-hosts = [\"*.tcpshield.com\"] }\n")
                        .protection()
                        .problems())
                .singleElement()
                .asString()
                .contains("shared-address-sources");
        assertThat(settingsFrom(folder, "").protection().problems()).isEmpty();
        assertThatThrownBy(() -> settingsFrom(folder, "protection { shared-address-sources = [\"tcpshield.com\"] }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protection.shared-address-sources");
    }

    @Test
    @DisplayName("the proxy commands allowed before login are read without their slash and in lower case")
    void theAllowedProxyCommandsAreRead(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "network { allowed-commands = [\"/GList\", \" server \"] }\n")
                        .network()
                        .allowedCommands())
                .containsExactly("glist", "server");
    }

    @Test
    @DisplayName("a backend on a network refuses to start with no secret, and says where the secret comes from")
    void aNetworkNeedsTheSecret(@TempDir Path folder) throws IOException {
        assertThatThrownBy(() -> settingsFrom(folder, "network { enabled = true }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("network.secret")
                .hasMessageContaining("proxy");
        AuthSettings.Network network = settingsFrom(
                        folder, "network { enabled = true, secret = \"" + "a".repeat(43) + "\", claim-seconds = 10 }\n")
                .network();
        assertThat(network.enabled()).isTrue();
        assertThat(network.secret()).hasSize(43);
        assertThat(network.claimFor()).isEqualTo(Duration.ofSeconds(10));
    }

    private static PluginSettings.Snapshot settingsFrom(Path folder, String body) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, body, StandardCharsets.UTF_8);
        return new PluginSettings(file).current();
    }
}
