package com.uxplima.auth.domain.premium;

/** What happens when a paid owner proves a name a cracked account holds, as the operator chose. */
public enum ConflictStrategy {
    /** The cracked account moves, with all its data, to the name and a mark; the owner gets the name. */
    RENAME,
    /** Both are stopped with a message until staff settle it with a command. */
    BLOCK,
    /** The cracked account keeps the name, and the paid owner is told to change theirs. */
    CRACKED_KEEPS,
    /** The cracked account is deleted, and the owner gets the name. Players can abuse this. */
    PREMIUM_TAKES
}
