package com.uxplima.auth.paper.bootstrap;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import javax.net.ssl.SSLSocketFactory;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.adapter.outbound.SqlEmails;
import com.uxplima.auth.adapter.outbound.mail.MailFiles;
import com.uxplima.auth.adapter.outbound.mail.SmtpClient;
import com.uxplima.auth.adapter.outbound.mail.SmtpMailer;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.mail.LinkEmail;
import com.uxplima.auth.application.mail.MailCodes;
import com.uxplima.auth.application.mail.MailLogin;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.paper.adapter.inbound.command.EmailCommand;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.hold.MomentRunner;
import com.uxplima.auth.paper.adapter.inbound.mail.BedrockEmailForms;
import com.uxplima.auth.paper.adapter.inbound.mail.EmailLink;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * Mail's part of the graph: the mailer on its own threads, the mail table, the codes, the login code mailed as the
 * second step opens, and {@code /email}. The core is built before the second step, which mails through it.
 */
final class MailGraph implements AutoCloseable {

    final MailCodes codes;
    final SqlEmails emails;
    private final MailLogin login;
    private final SmtpMailer mailer;
    private final Plugin plugin;
    private final PluginSettings settings;
    private final HashingLane lane;
    private final Messages messages;

    MailGraph(
            Plugin plugin,
            PluginSettings settings,
            Database database,
            LogIn.Failures failures,
            Hashing hashing,
            HashingLane lane,
            Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.lane = lane;
        this.messages = messages;
        Path folder = plugin.getDataFolder().toPath().resolve(MailFiles.FOLDER);
        this.mailer = new SmtpMailer(
                () -> settings.current().mail(),
                new MailFiles(folder),
                new SmtpClient((SSLSocketFactory) SSLSocketFactory.getDefault()),
                () -> settings.current().language().defaultLocale(),
                plugin.getLogger()::warning,
                Clock.systemUTC(),
                settings.current().mail().threads());
        this.emails = new SqlEmails(database, Clock.systemUTC());
        this.codes = new MailCodes(
                emails,
                mailer,
                hashing,
                lane,
                () -> settings.current().email().codes(),
                failures,
                new SecureRandom(),
                Instant::now);
        this.login = new MailLogin(emails, codes);
    }

    /**
     * Mails the login code as the account's second step opens, in the language of the player when they stand in
     * the world, else in the server's own.
     */
    void loginMailed(Account account) {
        Player online = plugin.getServer().getPlayerExact(account.name().value());
        Locale language = online == null ? settings.current().language().defaultLocale() : messages.localeOf(online);
        CompletableFuture<MailCodes.Issued> unused = login.send(account, language);
    }

    /** {@code /email}. */
    EmailCommand command(Sessions sessions, Predicate<UUID> bedrock, Replies replies, MomentRunner moments) {
        LinkEmail link =
                new LinkEmail(emails, codes, () -> settings.current().email().domains(), lane);
        return new EmailCommand(new EmailLink(
                link,
                sessions,
                new BedrockEmailForms(BedrockScreen.forServer(plugin.getServer()), messages),
                bedrock,
                lane,
                replies,
                moments,
                messages));
    }

    @Override
    public void close() {
        mailer.close();
    }
}
