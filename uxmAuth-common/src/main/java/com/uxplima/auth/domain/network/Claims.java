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
 * Signs a claim on the proxy and reads one on a backend, and the other way for a server's report, with
 * HMAC-SHA256.
 *
 * <p>The network's secret is never a signing key itself. Each use has a key of its own, made from the secret with
 * its own label: the proxy's claims, the servers' reports and the bulletins. A key one use leaks signs nothing for
 * another, and the text a key signs names its use as well.
 *
 * <p>A signed claim is the text and its signature, each in URL-safe Base64, joined by a dot. The signatures are
 * compared in constant time, so the time a refusal takes says nothing of how close a forgery came.
 */
public final class Claims {

    /** What a member makes of a claim. */
    public sealed interface Reading {

        /** Signed, fresh, about this player, for this server, and never taken before. */
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

    /** The forwarded profile property a claim travels in, from the proxy to a server. */
    public static final String PROPERTY = "uxmauth";

    /** The plugin channel a report travels on, from a server to the proxy. */
    public static final String CHANNEL = "uxmauth:network";

    private static final String ALGORITHM = "HmacSHA256";

    // The labels each use's key is made with; the second factor's seal makes its key with a label of its own.
    private static final String CLAIM_LABEL = "uxmauth claim v2";
    private static final String REPORT_LABEL = "uxmauth report v2";
    private static final String BULLETIN_LABEL = "uxmauth bulletin v2";

    /** How far ahead of a backend's clock a proxy's may run before a claim reads as from the future. */
    private static final Duration DRIFT = Duration.ofSeconds(5);

    private final byte[] claimKey;
    private final byte[] reportKey;
    private final byte[] bulletinKey;
    private final Duration fresh;
    private final Nonces seen;

    /** A signer with a nonce memory of its own, for a member that only signs. */
    public Claims(NetworkSecret secret, Duration fresh) {
        this(secret, fresh, new Nonces());
    }

    /** @param seen the nonces of the claims this member took, kept beyond this reader so a copy is noticed */
    public Claims(NetworkSecret secret, Duration fresh, Nonces seen) {
        Objects.requireNonNull(secret, "secret");
        this.fresh = Objects.requireNonNull(fresh, "fresh");
        this.seen = Objects.requireNonNull(seen, "seen");
        this.claimKey = mac(secret.key(), CLAIM_LABEL.getBytes(StandardCharsets.US_ASCII));
        this.reportKey = mac(secret.key(), REPORT_LABEL.getBytes(StandardCharsets.US_ASCII));
        this.bulletinKey = mac(secret.key(), BULLETIN_LABEL.getBytes(StandardCharsets.US_ASCII));
    }

    /** The claim, signed for the way it travels. */
    public String sign(Claim claim, Claim.Direction direction) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(direction, "direction");
        return seal(claim.text(direction), keyOf(direction));
    }

    /**
     * What a member makes of a signed claim that arrived with this player: on a backend a claim written for this
     * server, on the proxy a report written by the server it came from. A valid one is taken once; a copy of it
     * is refused while it is fresh.
     *
     * @param server the server the claim must name: this server's own name, or the name of the one that reported
     */
    public Reading read(String sealed, Claim.Direction direction, UUID id, String name, String server, Instant now) {
        Objects.requireNonNull(sealed, "sealed");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(now, "now");
        int dot = sealed.indexOf('.');
        Optional<byte[]> text = decode(dot < 0 ? "" : sealed.substring(0, dot));
        Optional<byte[]> signature = decode(dot < 0 ? "" : sealed.substring(dot + 1));
        if (text.isEmpty() || signature.isEmpty()) {
            return new Reading.Refused(ClaimRefusal.MALFORMED);
        }
        String written = new String(text.get(), StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(signature.get(), mac(keyOf(direction), text.get()))) {
            // An older release signs with the bare secret; its claim is refused, and the console says to update.
            return new Reading.Refused(
                    written.startsWith(Claim.OUTDATED) ? ClaimRefusal.OUTDATED : ClaimRefusal.BAD_SIGNATURE);
        }
        Optional<Claim> claim = Claim.parse(written, direction);
        if (claim.isEmpty()) {
            return new Reading.Refused(ClaimRefusal.MALFORMED);
        }
        Claim read = claim.get();
        if (!fresh(read.issued(), now)) {
            return new Reading.Refused(ClaimRefusal.STALE);
        }
        if (!read.id().equals(id) || !read.name().equals(name)) {
            return new Reading.Refused(ClaimRefusal.NOT_THIS_PLAYER);
        }
        if (!read.names(server)) {
            return new Reading.Refused(ClaimRefusal.WRONG_SERVER);
        }
        if (!seen.first(read.nonce(), read.issued().plus(fresh), now)) {
            return new Reading.Refused(ClaimRefusal.REPLAYED);
        }
        return new Reading.Valid(read);
    }

    /** Whether a text issued then is still fresh now, allowing the other clock a little drift ahead. */
    boolean fresh(Instant issued, Instant now) {
        return !now.isAfter(issued.plus(fresh)) && !issued.isAfter(now.plus(DRIFT));
    }

    /** The text of a bulletin sealed with the bulletins' key; nothing when it is malformed or its signature is wrong. */
    Optional<String> unsealBulletin(String sealed) {
        int dot = sealed.indexOf('.');
        Optional<byte[]> text = decode(dot < 0 ? "" : sealed.substring(0, dot));
        Optional<byte[]> signature = decode(dot < 0 ? "" : sealed.substring(dot + 1));
        if (text.isEmpty()
                || signature.isEmpty()
                || !MessageDigest.isEqual(signature.get(), mac(bulletinKey, text.get()))) {
            return Optional.empty();
        }
        return Optional.of(new String(text.get(), StandardCharsets.UTF_8));
    }

    /** A bulletin's text, sealed with the bulletins' key. */
    String sealBulletin(String text) {
        return seal(text, bulletinKey);
    }

    private byte[] keyOf(Claim.Direction direction) {
        return switch (direction) {
            case TO_SERVER -> claimKey;
            case TO_PROXY -> reportKey;
        };
    }

    private static String seal(String text, byte[] key) {
        byte[] bytes = Objects.requireNonNull(text, "text").getBytes(StandardCharsets.UTF_8);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(bytes) + "." + encoder.encodeToString(mac(key, bytes));
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

    private static byte[] mac(byte[] key, byte[] text) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(text);
        } catch (GeneralSecurityException missing) {
            throw new IllegalStateException("This Java has no " + ALGORITHM + ", which every Java must have", missing);
        }
    }
}
