package com.uxplima.auth.domain.factor;

/** A way a player proves the login a second time, after the first step. */
public enum FactorKind {
    /** A code from an authenticator app. */
    APP,
    /** A code mailed to the linked address as the step opens. */
    EMAIL
}
