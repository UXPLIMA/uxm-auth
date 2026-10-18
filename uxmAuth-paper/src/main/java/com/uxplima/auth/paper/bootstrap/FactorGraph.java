package com.uxplima.auth.paper.bootstrap;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.adapter.outbound.SecretSeal;
import com.uxplima.auth.adapter.outbound.SqlFactors;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.factor.ConfirmFactor;
import com.uxplima.auth.application.factor.FactorCheck;
import com.uxplima.auth.application.factor.SecondStep;
import com.uxplima.auth.application.factor.SetUpTotp;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.mail.MailFactor;
import com.uxplima.auth.application.mail.MailedLoginCode;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.network.NetworkSecret;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.command.TwoFactorCommand;
import com.uxplima.auth.paper.adapter.inbound.factor.BedrockSetupForm;
import com.uxplima.auth.paper.adapter.inbound.factor.FactorLogin;
import com.uxplima.auth.paper.adapter.inbound.factor.FactorSetup;
import com.uxplima.auth.paper.adapter.inbound.factor.MailToggle;
import com.uxplima.auth.paper.adapter.inbound.factor.OwedSetups;
import com.uxplima.auth.paper.adapter.inbound.factor.QrMapGuard;
import com.uxplima.auth.paper.adapter.inbound.factor.SetupDuty;
import com.uxplima.auth.paper.adapter.inbound.hold.MomentRunner;
import com.uxplima.auth.paper.adapter.inbound.screen.AuthScreens;
import com.uxplima.auth.paper.adapter.inbound.screen.FactorScreen;
import com.uxplima.auth.paper.adapter.inbound.screen.Guest;
import com.uxplima.auth.paper.adapter.inbound.screen.ScreenShower;
import com.uxplima.auth.paper.adapter.outbound.factor.QrMap;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * The second factor's part of the graph: the sealed table, the step after the password and its screen, the setup,
 * the QR map, the command and its guard.
 */
final class FactorGraph {

    /** Where a server alone keeps the key its secrets are sealed with. */
    static final String KEY_FILE = "second-factor.key";

    final SecondStep secondStep;
    final FactorScreen screen;
    final QrMapGuard guard;
    /** Holds a player whose node requires the app until it is on. */
    final SetupDuty duty;

    private final FactorSetup setup;
    private final MailToggle mailToggle;
    private final Sessions sessions;
    private final Messages messages;

    FactorGraph(
            Plugin plugin,
            PluginSettings settings,
            Database database,
            AccountRepository accounts,
            Sessions sessions,
            LogIn.Failures failures,
            Predicate<UUID> bedrock,
            Hashing hashing,
            HashingLane lane,
            Replies replies,
            MomentRunner moments,
            Messages messages,
            AuthScreens screens,
            ScreenShower shower,
            MailGraph mail) {
        this.sessions = sessions;
        this.messages = messages;
        Path folder = plugin.getDataFolder().toPath();
        AuthSettings.Network network = settings.current().network();
        SqlFactors factors =
                new SqlFactors(database, seal(network.enabled(), network.secret(), folder), Clock.systemUTC());
        this.secondStep = new SecondStep(
                factors, mail.emails, () -> settings.current().secondFactor().step(), mail::loginMailed);
        FactorCheck check = new FactorCheck(
                factors,
                hashing,
                () -> settings.current().secondFactor().setup().totp(),
                () -> settings.current().secondFactor().setup().backup(),
                new MailedLoginCode(mail.emails, mail.codes));
        ConfirmFactor confirm = new ConfirmFactor(accounts, sessions, check, failures, Instant::now);
        this.screen = new FactorScreen(confirm, screens, shower, messages, lane);
        SetUpTotp setUp = new SetUpTotp(
                factors,
                check,
                () -> settings.current().secondFactor().setup(),
                failures,
                new SecureRandom(),
                Instant::now);
        Optional<QrMap> qr = QrMap.open(plugin.getServer(), folder.resolve("qr-map.id"), plugin.getLogger());
        OwedSetups owed = new OwedSetups(sessions);
        this.setup = new FactorSetup(
                setUp,
                sessions,
                owed,
                () -> settings.current().secondFactor().setup(),
                qr.<FactorSetup.Hand>map(map -> map).orElse(FactorSetup.Hand.NONE),
                new BedrockSetupForm(BedrockScreen.forServer(plugin.getServer()), messages),
                bedrock,
                lane,
                replies,
                moments,
                messages);
        this.guard = new QrMapGuard(
                item -> qr.map(map -> map.holds(item)).orElse(false),
                player -> qr.ifPresent(map -> map.take(player)),
                setup::left);
        this.mailToggle = new MailToggle(
                new MailFactor(mail.emails, MailFactor.by(check, Instant::now), failures),
                sessions,
                lane,
                replies,
                moments,
                messages);
        this.duty = new SetupDuty(
                owed,
                () -> settings.current().secondFactor().requiredPermission(),
                id -> setUp.active(id) || mail.emails.mailsLoginCode(id),
                setup,
                bedrock,
                lane,
                replies,
                messages);
    }

    /** {@code /2fa}, whose code of the second step reaches the guest each player is. */
    TwoFactorCommand command(Function<Player, Guest> guests) {
        return new TwoFactorCommand(setup, new FactorLogin(sessions, screen, guests, messages), mailToggle);
    }

    /**
     * The key secrets are sealed with: on a network the secret every member shares, so any server opens what
     * another sealed, and the server's own key file too where it ran alone before; on a server alone a key made
     * once in the data folder.
     */
    static SecretSeal seal(boolean networked, String secret, Path folder) {
        Path file = folder.resolve(KEY_FILE);
        if (!networked) {
            return SecretSeal.fromKeyFile(file, new SecureRandom());
        }
        // Read as the proxy reads its own, so a secret written with a stray space seals alike on both.
        SecretSeal shared =
                SecretSeal.fromPassphrase(NetworkSecret.parse(secret).encoded());
        return SecretSeal.readKeyFile(file).map(shared::openingAlso).orElse(shared);
    }
}
