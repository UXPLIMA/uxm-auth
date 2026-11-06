package com.uxplima.auth.adapter.outbound.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CountryDownloadTest {

    private static final URI STORAGE = URI.create("https://storage.example.net/GeoLite2-Country.tar.gz?signed=x");

    @TempDir
    Path folder;

    private final List<String> console = new ArrayList<>();

    /** One answer of the fake web: a status and a Location header, if any. */
    private record Answer(int status, Optional<String> location) {
        static Answer of(int status) {
            return new Answer(status, Optional.empty());
        }

        static Answer redirect(int status, String location) {
            return new Answer(status, Optional.of(location));
        }
    }

    private CountryDownload download(Web web) {
        return new CountryDownload(
                web,
                CountryDownload.MAXMIND,
                folder,
                () -> new GeoSettings.Licence("123456", "a licence only these tests use"),
                () -> Duration.ofDays(7),
                Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneOffset.UTC),
                console::add);
    }

    @Test
    void theRedirectIsFollowedWithoutTheLicence() {
        Web web = new Web(List.of(Answer.redirect(302, STORAGE.toString()), Answer.of(404)));

        download(web).refresh();

        assertThat(web.asked).hasSize(2);
        assertThat(web.asked.get(0).headers().firstValue("Authorization")).isPresent();
        assertThat(web.asked.get(1).uri()).isEqualTo(STORAGE);
        assertThat(web.asked.get(1).headers().firstValue("Authorization")).isEmpty();
    }

    @Test
    void aRedirectToPlainHttpIsNotFollowed() {
        Web web = new Web(List.of(Answer.redirect(302, "http://storage.example.net/file")));

        assertThat(download(web).refresh()).isEqualTo(CountryDownload.Outcome.FAILED);

        assertThat(web.asked).hasSize(1);
        assertThat(console).singleElement().asString().contains("not HTTPS").doesNotContain("storage.example.net");
    }

    @Test
    void endlessRedirectsAreGivenUp() {
        Web web = new Web(List.of(
                Answer.redirect(302, STORAGE.toString()),
                Answer.redirect(302, STORAGE.toString()),
                Answer.redirect(302, STORAGE.toString()),
                Answer.redirect(302, STORAGE.toString()),
                Answer.redirect(302, STORAGE.toString())));

        assertThat(download(web).refresh()).isEqualTo(CountryDownload.Outcome.FAILED);

        assertThat(web.asked).hasSize(4);
    }

    @Test
    void aRefusedLicenceSaysSo() {
        Web web = new Web(List.of(Answer.of(401)));

        assertThat(download(web).refresh()).isEqualTo(CountryDownload.Outcome.FAILED);

        assertThat(console).singleElement().asString().contains("401").contains("licence-key");
    }

    @Test
    void anotherAnswerDoesNotBlameTheLicence() {
        Web web = new Web(List.of(Answer.of(500)));

        assertThat(download(web).refresh()).isEqualTo(CountryDownload.Outcome.FAILED);

        assertThat(console).singleElement().asString().contains("500").doesNotContain("licence");
    }

    /** A web that answers each request with the next answer, and keeps what it was asked. */
    private static final class Web extends HttpClient {

        private final List<Answer> answers;
        final List<HttpRequest> asked = new ArrayList<>();

        Web(List<Answer> answers) {
            this.answers = answers;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            asked.add(request);
            Answer answer = answers.get(asked.size() - 1);
            Map<String, List<String>> headers =
                    answer.location().map(at -> Map.of("Location", List.of(at))).orElse(Map.of());
            return (HttpResponse<T>)
                    new Response(request, answer.status(), HttpHeaders.of(headers, (name, value) -> true));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }
    }

    private record Response(HttpRequest request, int statusCode, HttpHeaders headers)
            implements HttpResponse<InputStream> {

        @Override
        public Optional<HttpResponse<InputStream>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public InputStream body() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
