package com.uxplima.auth.velocity.bootstrap;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SSLSocketFactory;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.adapter.outbound.SqlEmails;
import com.uxplima.auth.adapter.outbound.mail.MailFiles;
import com.uxplima.auth.adapter.outbound.mail.SmtpClient;
import com.uxplima.auth.adapter.outbound.mail.SmtpMailer;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.mail.MailCodes;
import com.uxplima.auth.application.mail.MailLogin;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.text.message.Messages;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

/**
 * Mail at the proxy: the mailer on its own threads, the table the servers share, the codes, and the login code
 * mailed as a second step opens here. Codes are hashed on a lane of its own, so mail never waits behind the
 * waiting world's passwords.
 */
final class ProxyMail implements AutoCloseable {

    final SqlEmails emails;
    final MailCodes codes;
    private final MailLogin login;
    private final SmtpMailer mailer;
    private final HashingLane lane;
    private final ProxyServer proxy;
    private final PluginSettings settings;
    private final Messages words;

    ProxyMail(
            ProxyServer proxy,
            Logger logger,
            Path folder,
            PluginSettings settings,
            Database database,
            LogIn.Failures failures,
            Messages words) {
        this.proxy = proxy;
        this.settings = settings;
        this.words = words;
        PluginSettings.Snapshot boot = settings.current();
        AuthSettings.Lane sizes = boot.lane();
        this.lane = new HashingLane(1, sizes.maxThreads(), sizes.queue());
        this.mailer = new SmtpMailer(
                () -> settings.current().mail(),
                new MailFiles(folder.resolve(MailFiles.FOLDER)),
                new SmtpClient((SSLSocketFactory) SSLSocketFactory.getDefault()),
                () -> settings.current().language().defaultLocale(),
                logger::warn,
                Clock.systemUTC(),
                boot.mail().threads());
        this.emails = new SqlEmails(database, Clock.systemUTC());
        this.codes = new MailCodes(
                emails,
                mailer,
                boot.hashers(),
                lane,
                () -> settings.current().email().codes(),
                failures,
                new SecureRandom(),
                Instant::now);
        this.login = new MailLogin(emails, codes);
    }

    /** Mails the login code as the account's second step opens here, in the player's language when they are on. */
    void loginMailed(Account account) {
        Locale language = proxy.getPlayer(account.name().value())
                .map(words::localeOf)
                .orElseGet(() -> settings.current().language().defaultLocale());
        CompletableFuture<MailCodes.Issued> unused = login.send(account, language);
    }

    @Override
    public void close() {
        mailer.close();
        lane.close();
    }
}
