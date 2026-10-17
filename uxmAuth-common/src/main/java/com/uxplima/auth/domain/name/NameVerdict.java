package com.uxplima.auth.domain.name;

import java.util.Objects;

/** What the name policy said about a name a player connected with. */
public sealed interface NameVerdict {

    /** The name may be used. */
    record Accepted(PlayerName name) implements NameVerdict {
        public Accepted {
            Objects.requireNonNull(name, "name");
        }
    }

    /** The name is shorter than the minimum. */
    record TooShort(int minimum) implements NameVerdict {}

    /** The name is longer than the maximum. */
    record TooLong(int maximum) implements NameVerdict {}

    /** The name holds a character the operator's pattern does not allow. */
    record BadCharacters() implements NameVerdict {}

    /** The name holds a word the operator blocked. */
    record Blocked(String word) implements NameVerdict {
        public Blocked {
            Objects.requireNonNull(word, "word");
        }
    }

    /** The name is registered in another case, which is named so the player can join with it. */
    record WrongCase(PlayerName registered) implements NameVerdict {
        public WrongCase {
            Objects.requireNonNull(registered, "registered");
        }
    }
}
