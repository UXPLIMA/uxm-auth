package com.uxplima.auth.domain.hash;

/** The algorithms a password is hashed with. Every one of them is also read when an old hash meets it. */
public enum HashAlgorithm {
    ARGON2ID,
    BCRYPT,
    PBKDF2,
    /** AuthMe's salted double SHA-256, written only for a website that still expects it. */
    AUTHME_SHA256
}
