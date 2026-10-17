package com.uxplima.auth.domain.network;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs a claim on the proxy and reads one on a backend, with HMAC-SHA256 over the network's secret.
 *
 * <p>A signed claim is the text and its signature, each in URL-safe Base64, joined by a dot. The signatures are
 * compared in constant time, so the time a refusal takes says nothing of how close a forgery came.
 */
public final class Claims {

    /** What a backend makes of a claim. */
    public sealed interface Reading {

        /** Signed, fresh and about this player. */
        record Valid(Claim claim) implements Reading {
            public Valid {
                Objects.requireNonNull(claim, "claim");
            }
        }

        /** Refused, for this reason. */
        record Refused(ClaimRefusal why) implements Reading {
            public Refused {
                Objects.requireNonNull(why, "why");
            }
        }
    }

    private static final String ALGORITHM = "HmacSHA256";

    /** How far ahead of a backend's clock a proxy's may run before a claim reads as from the future. */
    private static final Duration DRIFT = Duration.ofSeconds(5);

    private final NetworkSecret secret;
    private final Duration fresh;

    public Claims(NetworkSecret secret, Duration fresh) {
        this.secret = Objects.requireNonNull(secret, "secret");
        this.fresh = Objects.requireNonNull(fresh, "fresh");
    }

    /** The claim, signed. */
    public String sign(Claim claim) {
        return seal(Objects.requireNonNull(claim, "claim").text());
    }

    /** What a backend makes of a signed claim that arrived with this player. */
    public Reading read(String sealed, UUID id, String name, Instant now) {
        Objects.requireNonNull(sealed, "sealed");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(now, "now");
        int dot = sealed.indexOf('.');
        Optional<byte[]> text = decode(dot < 0 ? "" : sealed.substring(0, dot));
        Optional<byte[]> signature = decode(dot < 0 ? "" : sealed.substring(dot + 1));
        if (text.isEmpty() || signature.isEmpty()) {
            return new Reading.Refused(ClaimRefusal.MALFORMED);
        }
        if (!MessageDigest.isEqual(signature.get(), mac(text.get()))) {
            return new Reading.Refused(ClaimRefusal.BAD_SIGNATURE);
        }
        Optional<Claim> claim = Claim.parse(new String(text.get(), StandardCharsets.UTF_8));
        if (claim.isEmpty()) {
            return new Reading.Refused(ClaimRefusal.MALFORMED);
        }
        Claim read = claim.get();
        if (now.isAfter(read.issued().plus(fresh)) || read.issued().isAfter(now.plus(DRIFT))) {
            return new Reading.Refused(ClaimRefusal.STALE);
        }
        if (!read.id().equals(id) || !read.name().equals(name)) {
            return new Reading.Refused(ClaimRefusal.NOT_THIS_PLAYER);
        }
        return new Reading.Valid(read);
    }

    /** Any text, signed the way a claim is. */
    String seal(String text) {
        byte[] bytes = Objects.requireNonNull(text, "text").getBytes(StandardCharsets.UTF_8);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(bytes) + "." + encoder.encodeToString(mac(bytes));
    }

    private static Optional<byte[]> decode(String part) {
        if (part.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Base64.getUrlDecoder().decode(part));
        } catch (IllegalArgumentException notBase64) {
            return Optional.empty();
        }
    }

    private byte[] mac(byte[] text) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.key(), ALGORITHM));
            return mac.doFinal(text);
        } catch (GeneralSecurityException missing) {
            throw new IllegalStateException("This Java has no " + ALGORITHM + ", which every Java must have", missing);
        }
    }
}
