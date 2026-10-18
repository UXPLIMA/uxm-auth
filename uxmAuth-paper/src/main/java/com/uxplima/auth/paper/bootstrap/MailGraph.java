package com.uxplima.auth.paper.bootstrap;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Predicate;

import javax.net.ssl.SSLSocketFactory;

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
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.paper.adapter.inbound.command.EmailCommand;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.hold.MomentRunner;
import com.uxplima.auth.paper.adapter.inbound.mail.BedrockEmailForms;
import com.uxplima.auth.paper.adapter.inbound.mail.EmailLink;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.text.message.Messages;

/** Mail's part of the graph: the mailer on its own threads, the mail table, the codes, and {@code /email}. */
final class MailGraph implements AutoCloseable {

    final MailCodes codes;
    final SqlEmails emails;
    final EmailCommand command;
    private final SmtpMailer mailer;

    MailGraph(
            Plugin plugin,
            PluginSettings settings,
            Database database,
            Sessions sessions,
            LogIn.Failures failures,
            Predicate<UUID> bedrock,
            Hashing hashing,
            HashingLane lane,
            Replies replies,
            MomentRunner moments,
            Messages messages) {
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
        LinkEmail link =
                new LinkEmail(emails, codes, () -> settings.current().email().domains(), lane);
        this.command = new EmailCommand(new EmailLink(
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
