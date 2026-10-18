package com.uxplima.auth.adapter.outbound.mail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.uxplima.auth.adapter.outbound.MailSettings;
import com.uxplima.auth.domain.mail.EmailAddress;

/**
 * One mail over SMTP (RFC 5321): in the clear, over TLS from the first byte, or upgraded by STARTTLS, with AUTH
 * PLAIN or LOGIN when the operator gives a login. A connection asked for STARTTLS never falls back to the clear,
 * and TLS checks the server's name against its certificate. It runs on the caller's thread, which is the mailer's.
 */
public final class SmtpClient {

    private static final String CRLF = "\r\n";
    /** The two commands of the envelope, each followed by an address in angle brackets. */
    private static final String SENDER = "MAIL FROM";

    private static final String RECIPIENT = "RCPT TO";

    private final SSLSocketFactory tls;

    /** @param tls how TLS connections are made; a server passes the JDK's default */
    public SmtpClient(SSLSocketFactory tls) {
        this.tls = Objects.requireNonNull(tls, "tls");
    }

    /** Sends the message to the address, from the settings' sender, through the settings' server. */
    public void send(MailSettings settings, String to, String message) throws IOException {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(message, "message");
        if (EmailAddress.parse(Objects.requireNonNull(to, "to")).isEmpty()) {
            throw new IllegalArgumentException("not a mail address");
        }
        try (Socket socket = connect(settings)) {
            Conversation talk = new Conversation(socket);
            talk.expect("CONNECT", 220);
            String hello = "EHLO " + helloName(settings.fromAddress());
            List<String> offers = talk.command(hello, "EHLO", 250);
            if (settings.security() == MailSettings.Security.STARTTLS) {
                talk = startTls(settings, socket, talk, offers);
                offers = talk.command(hello, "EHLO", 250);
            }
            if (!settings.username().isEmpty()) {
                logIn(talk, settings, offers);
            }
            talk.command(envelope(SENDER, settings.fromAddress()), "MAIL", 250);
            talk.command(envelope(RECIPIENT, to), "RCPT", 250);
            talk.command("DATA", "DATA", 354);
            talk.data(message);
            talk.expect("DATA", 250);
            talk.command("QUIT", "QUIT", 221);
        }
    }

    private Socket connect(MailSettings settings) throws IOException {
        int timeout = (int) settings.timeout().toMillis();
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(settings.host(), settings.port()), timeout);
            socket.setSoTimeout(timeout);
            return settings.security() == MailSettings.Security.SSL ? secure(socket, settings) : socket;
        } catch (IOException | RuntimeException failed) {
            socket.close();
            throw failed;
        }
    }

    private Conversation startTls(MailSettings settings, Socket socket, Conversation talk, List<String> offers)
            throws IOException {
        if (!offers.contains("STARTTLS")) {
            throw new SmtpRefused("STARTTLS", "the server offers no STARTTLS, and the mail is not sent in the clear");
        }
        talk.command("STARTTLS", "STARTTLS", 220);
        return new Conversation(secure(socket, settings));
    }

    private SSLSocket secure(Socket plain, MailSettings settings) throws IOException {
        SSLSocket socket = (SSLSocket) tls.createSocket(plain, settings.host(), settings.port(), true);
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        socket.setSSLParameters(parameters);
        socket.startHandshake();
        return socket;
    }

    private static void logIn(Conversation talk, MailSettings settings, List<String> offers) throws IOException {
        List<String> mechanisms = offers.stream()
                .filter(offer -> offer.startsWith("AUTH "))
                .flatMap(offer -> List.of(offer.substring(5).split(" ")).stream())
                .toList();
        if (mechanisms.contains("PLAIN")) {
            String both = "\0" + settings.username() + "\0" + settings.password();
            talk.command("AUTH PLAIN " + base64(both), "AUTH", 235);
        } else if (mechanisms.contains("LOGIN")) {
            talk.command("AUTH LOGIN", "AUTH", 334);
            talk.command(base64(settings.username()), "AUTH", 334);
            talk.command(base64(settings.password()), "AUTH", 235);
        } else {
            throw new SmtpRefused("AUTH", "the server offers neither PLAIN nor LOGIN");
        }
    }

    private static String envelope(String command, String address) {
        return command + ":<" + address + ">";
    }

    /** The domain of the sender, which names this side in EHLO. */
    private static String helloName(String fromAddress) {
        return EmailAddress.parse(fromAddress).map(EmailAddress::domain).orElse("localhost");
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /** The lines both ways over one socket. */
    private static final class Conversation {

        private final BufferedReader in;
        private final OutputStream out;

        Conversation(Socket socket) throws IOException {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            this.out = socket.getOutputStream();
        }

        /** Sends the line and reads the reply, which must carry this code; answers its lines without the codes. */
        List<String> command(String line, String step, int code) throws IOException {
            out.write((line + CRLF).getBytes(StandardCharsets.UTF_8));
            out.flush();
            return expect(step, code);
        }

        /** The message, dot-stuffed, and the line with the single dot that ends it. */
        void data(String message) throws IOException {
            StringBuilder stuffed = new StringBuilder();
            for (String line : message.split(CRLF, -1)) {
                stuffed.append(line.startsWith(".") ? "." : "").append(line).append(CRLF);
            }
            String text = stuffed.toString();
            if (text.endsWith(CRLF + CRLF)) {
                text = text.substring(0, text.length() - CRLF.length());
            }
            out.write((text + "." + CRLF).getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        List<String> expect(String step, int code) throws IOException {
            List<String> lines = new ArrayList<>();
            String line;
            do {
                line = in.readLine();
                if (line == null || line.length() < 3) {
                    throw new SmtpRefused(step, "the server closed the connection");
                }
                lines.add(line.length() > 4 ? line.substring(4).toUpperCase(Locale.ROOT) : "");
            } while (line.length() > 3 && line.charAt(3) == '-');
            int answered = replyCode(line, step);
            if (answered != code) {
                throw new SmtpRefused(step, answered);
            }
            return lines;
        }

        private static int replyCode(String line, String step) throws SmtpRefused {
            try {
                return Integer.parseInt(line.substring(0, 3));
            } catch (NumberFormatException garbled) {
                throw new SmtpRefused(step, "the server's reply was not SMTP");
            }
        }
    }
}
