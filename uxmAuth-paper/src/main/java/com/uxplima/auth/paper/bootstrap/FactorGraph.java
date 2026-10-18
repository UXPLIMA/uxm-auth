package com.uxplima.auth.paper.bootstrap;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.plugin.Plugin;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.adapter.outbound.SecretSeal;
import com.uxplima.auth.adapter.outbound.SqlFactors;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.factor.SetUpTotp;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.command.TwoFactorCommand;
import com.uxplima.auth.paper.adapter.inbound.factor.BedrockSetupForm;
import com.uxplima.auth.paper.adapter.inbound.factor.FactorSetup;
import com.uxplima.auth.paper.adapter.inbound.factor.QrMapGuard;
import com.uxplima.auth.paper.adapter.inbound.hold.MomentRunner;
import com.uxplima.auth.paper.adapter.outbound.factor.QrMap;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.text.message.Messages;

/** The second factor's part of the graph: the sealed table, the setup, the QR map, the command and its guard. */
final class FactorGraph {

    /** Where a server alone keeps the key its secrets are sealed with. */
    static final String KEY_FILE = "second-factor.key";

    final TwoFactorCommand command;
    final QrMapGuard guard;

    FactorGraph(
            Plugin plugin,
            PluginSettings settings,
            Database database,
            Sessions sessions,
            LogIn.Failures failures,
            Predicate<UUID> bedrock,
            Scheduler scheduler,
            Replies replies,
            MomentRunner moments,
            Messages messages) {
        Path folder = plugin.getDataFolder().toPath();
        AuthSettings.Network network = settings.current().network();
        SqlFactors factors =
                new SqlFactors(database, seal(network.enabled(), network.secret(), folder), Clock.systemUTC());
        SetUpTotp setUp = new SetUpTotp(
                factors, () -> settings.current().secondFactor().setup(), failures, new SecureRandom(), Instant::now);
        Optional<QrMap> qr = QrMap.open(plugin.getServer(), folder.resolve("qr-map.id"), plugin.getLogger());
        FactorSetup setup = new FactorSetup(
                setUp,
                sessions,
                () -> settings.current().secondFactor().setup(),
                qr.<FactorSetup.Hand>map(map -> map).orElse(FactorSetup.Hand.NONE),
                new BedrockSetupForm(BedrockScreen.forServer(plugin.getServer()), messages),
                bedrock,
                scheduler::async,
                replies,
                moments,
                messages);
        this.command = new TwoFactorCommand(setup);
        this.guard = new QrMapGuard(
                item -> qr.map(map -> map.holds(item)).orElse(false),
                player -> qr.ifPresent(map -> map.take(player)),
                setup::left);
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
        SecretSeal shared = SecretSeal.fromPassphrase(secret);
        return SecretSeal.readKeyFile(file).map(shared::openingAlso).orElse(shared);
    }
}
