package com.uxplima.auth.domain.hash;

/** The algorithms a password is hashed with. Every one of them is also read when an old hash meets it. */
public enum HashAlgorithm {
    ARGON2ID,
    BCRYPT,
    PBKDF2,
    /** AuthMe's salted double SHA-256, written only for a website that still expects it. */
    AUTHME_SHA256,
    /** PBKDF2-HMAC-SHA256 in hex, written only for a website's login table set to pbkdf2. */
    PBKDF2_HEX,
    /** Argon2i, written only for a website's login table set to argon2. */
    ARGON2I,
    /**
     * An MD5 or SHA-2 digest, plain, salted or taken twice, as other login plugins wrote them. Read only, from an
     * import, and moved to the current algorithm at the next login.
     */
    LEGACY_DIGEST,
    /** Django's PBKDF2 with its key in base64, as some login plugins and websites wrote it. Read only. */
    PBKDF2_DJANGO
}
