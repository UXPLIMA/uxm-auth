package com.uxplima.auth.adapter.outbound.premium;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.uxplima.auth.application.port.NameSource;
import com.uxplima.auth.application.premium.PremiumAnswer;
import com.uxplima.auth.domain.name.PlayerName;

/**
 * One web source, asked over HTTP at the address config.conf gives it, with {@code {name}} replaced by the
 * name.
 *
 * <p>Every failure is an unknown answer: a refusal, a limit, a timeout, an address that does not resolve, or
 * a cap that says this source has been asked enough for now.
 */
public final class WebNameSource implements NameSource {

    private static final String AGENT = "uxmAuth";

    private final PremiumSource kind;
    private final HttpClient client;
    private final Supplier<String> address;
    private final Supplier<Duration> timeout;
    private final BooleanSupplier mayAsk;

    /** @param mayAsk asked before every request; a cap that is full answers false */
    public WebNameSource(
            PremiumSource kind,
            HttpClient client,
            Supplier<String> address,
            Supplier<Duration> timeout,
            BooleanSupplier mayAsk) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.client = Objects.requireNonNull(client, "client");
        this.address = Objects.requireNonNull(address, "address");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.mayAsk = Objects.requireNonNull(mayAsk, "mayAsk");
    }

    @Override
    public PremiumAnswer ask(PlayerName name) {
        Objects.requireNonNull(name, "name");
        if (!mayAsk.getAsBoolean()) {
            return new PremiumAnswer.Unknown();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(uri(name))
                    .timeout(timeout.get())
                    .header("User-Agent", AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return kind.read(response.statusCode(), response.body(), name);
        } catch (IOException | IllegalArgumentException failed) {
            return new PremiumAnswer.Unknown();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new PremiumAnswer.Unknown();
        }
    }

    private URI uri(PlayerName name) {
        return URI.create(address.get().replace("{name}", URLEncoder.encode(name.value(), StandardCharsets.UTF_8)));
    }

    /** The source this asks. */
    public PremiumSource kind() {
        return kind;
    }
}
