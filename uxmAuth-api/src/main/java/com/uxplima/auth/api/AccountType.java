package com.uxplima.auth.api;

/** What kind of account a player holds. */
public enum AccountType {
    /** A password account. */
    CRACKED,
    /** A paid account Mojang proves. */
    PREMIUM,
    /** A Bedrock account Xbox Live proves. */
    BEDROCK
}
