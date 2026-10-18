package com.uxplima.auth.testing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * An SMTP server in the test's own process, for one connection at a time: plain, TLS from the first byte, or
 * upgraded by STARTTLS. It records every line it was sent and the message of each DATA, and answers a command
 * the test names with the reply the test names. The certificate is made by the JDK's keytool for this run only.
 */
public final class FakeSmtp implements AutoCloseable {

    /** How the server meets a client. */
    public enum Mode {
        PLAIN,
        TLS,
        STARTTLS
    }

    private static final char[] PASS = "changeit".toCharArray();

    private final Mode mode;
    private final SSLContext context;
    private final ServerSocket server;
    private final Thread thread;
    private final List<String> lines = Collections.synchronizedList(new ArrayList<>());
    private final List<String> messages = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, String> replies = new ConcurrentHashMap<>();
    private volatile String auth = "PLAIN LOGIN";
    private volatile boolean offersStartTls = true;

    public FakeSmtp(Mode mode, Path folder) throws Exception {
        this(mode, folder, "localhost");
    }

    /** @param name the only name the certificate is good for */
    public FakeSmtp(Mode mode, Path folder, String name) throws Exception {
        this.mode = mode;
        this.context = context(keyStore(folder, name));
        this.server = mode == Mode.TLS
                ? context.getServerSocketFactory().createServerSocket(0, 1, InetAddress.getLoopbackAddress())
                : new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        this.thread = Thread.ofPlatform().daemon().name("fake-smtp").start(this::serve);
    }

    public int port() {
        return server.getLocalPort();
    }

    /** A socket factory that trusts this server's certificate and nothing else. */
    public SSLSocketFactory clientTls() {
        return context.getSocketFactory();
    }

    /** The mechanisms EHLO names after AUTH; empty names none. */
    public FakeSmtp auth(String mechanisms) {
        this.auth = mechanisms;
        return this;
    }

    /** Whether EHLO offers STARTTLS on a plain connection. */
    public FakeSmtp offersStartTls(boolean offers) {
        this.offersStartTls = offers;
        return this;
    }

    /** Answers the command that starts with this word with this reply instead. */
    public FakeSmtp answer(String command, String reply) {
        replies.put(command, reply);
        return this;
    }

    /** Every line the client sent, in order. */
    public List<String> lines() {
        return List.copyOf(lines);
    }

    /** The message of every DATA, as it arrived, dots unstuffed. */
    public List<String> messages() {
        return List.copyOf(messages);
    }

    /** Waits for the connection to end. */
    public void await() throws InterruptedException {
        thread.join(TimeUnit.SECONDS.toMillis(10));
    }

    @Override
    public void close() throws IOException {
        server.close();
    }

    private void serve() {
        try (Socket accepted = server.accept()) {
            accepted.setSoTimeout((int) TimeUnit.SECONDS.toMillis(10));
            talk(accepted);
        } catch (IOException ended) {
            // The test reads what was recorded; a dropped connection is part of some of them.
        }
    }

    private void talk(Socket socket) throws IOException {
        Socket current = socket;
        BufferedReader in = reader(current.getInputStream());
        OutputStream out = current.getOutputStream();
        say(out, "220 fake.smtp ready");
        String line;
        while ((line = in.readLine()) != null) {
            lines.add(line);
            String verb = line.split(" ", 2)[0].toUpperCase(java.util.Locale.ROOT);
            String override = replies.get(verb);
            if (override != null) {
                say(out, override);
                if (override.startsWith("221")) {
                    return;
                }
                continue;
            }
            switch (verb) {
                case "EHLO" -> ehlo(out, current instanceof SSLSocket);
                case "STARTTLS" -> {
                    say(out, "220 go ahead");
                    SSLSocket upgraded = (SSLSocket)
                            context.getSocketFactory().createSocket(current, "localhost", current.getPort(), true);
                    upgraded.setUseClientMode(false);
                    upgraded.startHandshake();
                    current = upgraded;
                    in = reader(current.getInputStream());
                    out = current.getOutputStream();
                }
                case "AUTH" -> authenticate(line, in, out);
                case "DATA" -> {
                    say(out, "354 end with a dot");
                    messages.add(data(in));
                    say(out, "250 queued");
                }
                case "QUIT" -> {
                    say(out, "221 bye");
                    return;
                }
                default -> say(out, "250 ok");
            }
        }
    }

    private void ehlo(OutputStream out, boolean secure) throws IOException {
        List<String> offers = new ArrayList<>();
        offers.add("fake.smtp");
        if (!secure && mode == Mode.STARTTLS && offersStartTls) {
            offers.add("STARTTLS");
        }
        if (!auth.isBlank()) {
            offers.add("AUTH " + auth);
        }
        offers.add("8BITMIME");
        for (int i = 0; i < offers.size(); i++) {
            say(out, "250" + (i == offers.size() - 1 ? " " : "-") + offers.get(i));
        }
    }

    private void authenticate(String line, BufferedReader in, OutputStream out) throws IOException {
        if (line.toUpperCase(java.util.Locale.ROOT).startsWith("AUTH LOGIN")) {
            say(out, "334 VXNlcm5hbWU6");
            lines.add(in.readLine());
            say(out, "334 UGFzc3dvcmQ6");
            lines.add(in.readLine());
        }
        say(out, "235 accepted");
    }

    private static String data(BufferedReader in) throws IOException {
        StringBuilder message = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null && !line.equals(".")) {
            message.append(line.startsWith(".") ? line.substring(1) : line).append("\r\n");
        }
        return message.toString();
    }

    private static BufferedReader reader(InputStream stream) {
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private static void say(OutputStream out, String reply) throws IOException {
        out.write((reply + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static KeyStore keyStore(Path folder, String name) throws Exception {
        Path file = folder.resolve(name + ".p12");
        if (!Files.isRegularFile(file)) {
            Process keytool = new ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", "keytool")
                                    .toString(),
                            "-genkeypair",
                            "-alias",
                            "smtp",
                            "-keyalg",
                            "EC",
                            "-validity",
                            "2",
                            "-dname",
                            "CN=" + name,
                            "-ext",
                            "localhost".equals(name) ? "SAN=dns:localhost,ip:127.0.0.1" : "SAN=dns:" + name,
                            "-keystore",
                            file.toString(),
                            "-storetype",
                            "PKCS12",
                            "-storepass",
                            new String(PASS))
                    .redirectErrorStream(true)
                    .start();
            byte[] said = keytool.getInputStream().readAllBytes();
            if (!keytool.waitFor(60, TimeUnit.SECONDS) || keytool.exitValue() != 0) {
                throw new IllegalStateException("keytool failed: " + new String(said, StandardCharsets.UTF_8));
            }
        }
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(file)) {
            store.load(in, PASS);
        }
        return store;
    }

    private static SSLContext context(KeyStore store) throws Exception {
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, PASS);
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
        return context;
    }
}
